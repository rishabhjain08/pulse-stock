#!/usr/bin/env node
'use strict';

// One-time setup: write SSM params, deploy bootstrap (S3 bucket), package Lambda, deploy main stack.
// Safe to re-run — all steps are idempotent.

const crypto = require('crypto');
const {
  loadEnv, INFRA_DIR,
  deployCfStack, putParam, getParam,
  packageLambda, uploadToS3,
  artifactBucket,
} = require('./_common');

const path = require('path');

(async () => {
  loadEnv();

  // ── SSM parameters ────────────────────────────────────────────────────────
  console.log('==> SSM parameters');
  await putParam('/poarvault/plaid-client-id',         process.env.PLAID_CLIENT_ID           || (() => { throw new Error('PLAID_CLIENT_ID missing') })());
  await putParam('/poarvault/plaid-secret',            process.env.PLAID_SECRET              || (() => { throw new Error('PLAID_SECRET missing') })());
  await putParam('/poarvault/plaid-env',               process.env.PLAID_ENV || 'sandbox', 'String');
  await putParam('/poarvault/splitwise-consumer-key',  process.env.SPLITWISE_CONSUMER_KEY    || (() => { throw new Error('SPLITWISE_CONSUMER_KEY missing') })());
  await putParam('/poarvault/splitwise-consumer-secret', process.env.SPLITWISE_CONSUMER_SECRET || (() => { throw new Error('SPLITWISE_CONSUMER_SECRET missing') })());

  const existingKey = await getParam('/poarvault/api-key');
  if (!existingKey) {
    const apiKey = crypto.randomBytes(32).toString('hex');
    await putParam('/poarvault/api-key', apiKey);
    console.log('');
    console.log('    >>> Generated API key — add to local.properties:');
    console.log(`        POARVAULT_API_KEY=${apiKey}`);
    console.log('');
  } else {
    console.log('    /poarvault/api-key already exists');
  }

  // ── Bootstrap stack (S3 artifact bucket) ─────────────────────────────────
  console.log('==> Deploying bootstrap stack (poarvault-bootstrap)');
  await deployCfStack('poarvault-bootstrap', path.join(INFRA_DIR, 'cloudformation/bootstrap.yaml'));
  const bucket = await artifactBucket();
  console.log(`    Artifact bucket: ${bucket}`);

  // ── Package + upload Lambda ───────────────────────────────────────────────
  console.log('==> Packaging Lambda');
  const { zipKey, zipPath } = packageLambda();
  await uploadToS3(bucket, zipKey, zipPath);
  require('fs').unlinkSync(zipPath);
  console.log(`    Uploaded s3://${bucket}/${zipKey}`);

  // ── Main stack ────────────────────────────────────────────────────────────
  console.log('==> Deploying main stack (poarvault)');
  await deployCfStack('poarvault', path.join(INFRA_DIR, 'cloudformation/template.yaml'), { LambdaS3Key: zipKey });

  console.log('');
  console.log('==> Setup complete');
  require('./get-outputs');
})().catch(e => { console.error(e.message); process.exit(1); });
