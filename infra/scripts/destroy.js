#!/usr/bin/env node
'use strict';

// Delete both CloudFormation stacks and all /poarvault SSM parameters.
// The S3 artifact bucket has DeletionPolicy: Retain — empty and delete it manually.

const readline = require('readline');
const {
  loadEnv,
  deleteCfStack, deleteParam,
  artifactBucket,
} = require('./_common');

(async () => {
  loadEnv();

  console.log('WARNING: Deletes the poarvault and poarvault-bootstrap stacks and all /poarvault SSM parameters.');
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  const answer = await new Promise(resolve => rl.question("Type 'yes' to confirm: ", resolve));
  rl.close();
  if (answer !== 'yes') { console.log('Aborted.'); process.exit(0); }

  const bucket = await artifactBucket().catch(() => null);

  console.log('==> Deleting main stack (poarvault)');
  await deleteCfStack('poarvault');
  console.log('    Done');

  console.log('==> Deleting bootstrap stack (poarvault-bootstrap)');
  await deleteCfStack('poarvault-bootstrap');
  console.log('    Done (bucket retained by DeletionPolicy: Retain)');

  console.log('==> Deleting SSM parameters');
  for (const p of ['/poarvault/plaid-client-id', '/poarvault/plaid-secret',
                   '/poarvault/plaid-env', '/poarvault/api-key']) {
    await deleteParam(p);
  }

  console.log('');
  console.log('==> Done.');
  if (bucket) {
    console.log(`    S3 bucket retained: ${bucket}`);
    console.log(`    To delete: aws s3 rb s3://${bucket} --force`);
  }
})().catch(e => { console.error(e.message); process.exit(1); });
