'use strict';

const path = require('path');
const fs   = require('fs');
const { execSync } = require('child_process');

const {
  CloudFormationClient,
  CreateChangeSetCommand,
  DescribeChangeSetCommand,
  DescribeStacksCommand,
  ExecuteChangeSetCommand,
  DeleteStackCommand,
} = require('@aws-sdk/client-cloudformation');
const { S3Client, PutObjectCommand }                           = require('@aws-sdk/client-s3');
const { SSMClient, PutParameterCommand, GetParameterCommand,
        DeleteParameterCommand }                               = require('@aws-sdk/client-ssm');
const { STSClient, GetCallerIdentityCommand }                  = require('@aws-sdk/client-sts');

const INFRA_DIR = path.resolve(__dirname, '..');

// ── Env ──────────────────────────────────────────────────────────────────────

function loadEnv() {
  const envFile = path.join(INFRA_DIR, '.env');
  if (!fs.existsSync(envFile)) {
    console.error('ERROR: infra/.env not found — copy infra/.env.template and fill in values.');
    process.exit(1);
  }
  require('dotenv').config({ path: envFile });
  for (const k of ['AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_REGION']) {
    if (!process.env[k]) {
      console.error(`ERROR: ${k} must be set in infra/.env`);
      process.exit(1);
    }
  }
}

function cfg() {
  return {
    region: process.env.AWS_REGION,
    credentials: {
      accessKeyId:     process.env.AWS_ACCESS_KEY_ID,
      secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
    },
  };
}

// ── Helpers ──────────────────────────────────────────────────────────────────

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function getStackOutput(stackName, outputKey) {
  const cf = new CloudFormationClient(cfg());
  const { Stacks } = await cf.send(new DescribeStacksCommand({ StackName: stackName }));
  return Stacks[0].Outputs?.find(o => o.OutputKey === outputKey)?.OutputValue ?? null;
}

async function artifactBucket() {
  return getStackOutput('poarvault-bootstrap', 'ArtifactBucketName');
}

// ── CloudFormation ────────────────────────────────────────────────────────────

async function deployCfStack(stackName, templateFile, parameters = {}) {
  const cf = new CloudFormationClient(cfg());
  const templateBody = fs.readFileSync(templateFile, 'utf8');
  const changeSetName = `cs-${Date.now()}`;

  // Decide CREATE vs UPDATE based on whether the stack already exists.
  let changeSetType = 'CREATE';
  try {
    const { Stacks } = await cf.send(new DescribeStacksCommand({ StackName: stackName }));
    if (Stacks[0].StackStatus !== 'REVIEW_IN_PROGRESS') changeSetType = 'UPDATE';
  } catch { /* stack doesn't exist — use CREATE */ }

  await cf.send(new CreateChangeSetCommand({
    StackName:      stackName,
    ChangeSetName:  changeSetName,
    ChangeSetType:  changeSetType,
    TemplateBody:   templateBody,
    Parameters:     Object.entries(parameters).map(([k, v]) => ({ ParameterKey: k, ParameterValue: v })),
    Capabilities:   ['CAPABILITY_NAMED_IAM'],
  }));

  // Poll until the change set is ready.
  let cs;
  while (true) {
    cs = await cf.send(new DescribeChangeSetCommand({ StackName: stackName, ChangeSetName: changeSetName }));
    if (cs.Status === 'CREATE_COMPLETE' || cs.Status === 'FAILED') break;
    await sleep(2000);
  }

  if (cs.Status === 'FAILED') {
    const reason = cs.StatusReason ?? '';
    if (reason.includes("didn't contain changes") || reason.includes('No updates')) {
      console.log(`    ${stackName}: already up to date`);
      return;
    }
    throw new Error(`Change set failed: ${reason}`);
  }

  await cf.send(new ExecuteChangeSetCommand({ StackName: stackName, ChangeSetName: changeSetName }));

  // Poll until the stack reaches a terminal state.
  const ok   = new Set(['CREATE_COMPLETE', 'UPDATE_COMPLETE']);
  const fail = new Set(['CREATE_FAILED', 'ROLLBACK_COMPLETE', 'UPDATE_FAILED',
                        'UPDATE_ROLLBACK_COMPLETE', 'ROLLBACK_FAILED']);
  while (true) {
    const { Stacks } = await cf.send(new DescribeStacksCommand({ StackName: stackName }));
    const status = Stacks[0].StackStatus;
    if (ok.has(status))   { console.log(`    ${stackName}: ${status}`); return; }
    if (fail.has(status)) throw new Error(`Stack ${stackName} in failed state: ${status}`);
    await sleep(3000);
  }
}

async function deleteCfStack(stackName) {
  const cf = new CloudFormationClient(cfg());
  await cf.send(new DeleteStackCommand({ StackName: stackName }));

  const done = new Set(['DELETE_COMPLETE']);
  const fail = new Set(['DELETE_FAILED']);
  while (true) {
    try {
      const { Stacks } = await cf.send(new DescribeStacksCommand({ StackName: stackName }));
      const status = Stacks[0].StackStatus;
      if (done.has(status))   return;
      if (fail.has(status))   throw new Error(`Stack ${stackName} delete failed`);
    } catch (e) {
      // DescribeStacks throws when the stack no longer exists — that means it's gone.
      if (e.message?.includes('does not exist')) return;
      throw e;
    }
    await sleep(3000);
  }
}

// ── SSM ───────────────────────────────────────────────────────────────────────

async function putParam(name, value, type = 'SecureString') {
  const ssm = new SSMClient(cfg());
  await ssm.send(new PutParameterCommand({ Name: name, Value: value, Type: type, Overwrite: true }));
  console.log(`    ${name}`);
}

async function getParam(name) {
  const ssm = new SSMClient(cfg());
  try {
    const { Parameter } = await ssm.send(new GetParameterCommand({ Name: name, WithDecryption: true }));
    return Parameter.Value;
  } catch { return null; }
}

async function deleteParam(name) {
  const ssm = new SSMClient(cfg());
  try {
    await ssm.send(new DeleteParameterCommand({ Name: name }));
    console.log(`    Deleted ${name}`);
  } catch { console.log(`    ${name} not found — skipped`); }
}

// ── S3 ────────────────────────────────────────────────────────────────────────

async function uploadToS3(bucket, key, filePath) {
  const s3 = new S3Client(cfg());
  await s3.send(new PutObjectCommand({ Bucket: bucket, Key: key, Body: fs.readFileSync(filePath) }));
}

// ── Lambda packaging ──────────────────────────────────────────────────────────

function packageLambda() {
  const lambdaDir = path.join(INFRA_DIR, 'lambda');
  const zipKey    = `lambda-${Date.now()}.zip`;
  const zipPath   = `/tmp/${zipKey}`;
  execSync('npm install --omit=dev --silent', { cwd: lambdaDir, stdio: 'inherit' });
  execSync(`zip -r ${zipPath} . -x "*.zip"`, { cwd: lambdaDir, stdio: 'pipe' });
  return { zipKey, zipPath };
}

// ── Account ───────────────────────────────────────────────────────────────────

async function getAccountId() {
  const sts = new STSClient(cfg());
  const { Account } = await sts.send(new GetCallerIdentityCommand({}));
  return Account;
}

module.exports = {
  INFRA_DIR, loadEnv, cfg,
  deployCfStack, deleteCfStack,
  putParam, getParam, deleteParam,
  uploadToS3, packageLambda,
  getAccountId, artifactBucket,
  getStackOutput,
};
