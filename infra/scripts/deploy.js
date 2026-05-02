#!/usr/bin/env node
'use strict';

// Re-package Lambda code and update the main CloudFormation stack.
// Run after any change to infra/lambda/.

const {
  loadEnv, INFRA_DIR,
  deployCfStack,
  packageLambda, uploadToS3,
  artifactBucket,
} = require('./_common');

const path = require('path');
const fs   = require('fs');

(async () => {
  loadEnv();

  const bucket = await artifactBucket();

  console.log('==> Packaging Lambda');
  const { zipKey, zipPath } = packageLambda();
  await uploadToS3(bucket, zipKey, zipPath);
  fs.unlinkSync(zipPath);
  console.log(`    Uploaded s3://${bucket}/${zipKey}`);

  console.log('==> Updating main stack (poarvault)');
  await deployCfStack('poarvault', path.join(INFRA_DIR, 'cloudformation/template.yaml'), { LambdaS3Key: zipKey });

  console.log('');
  console.log('==> Deploy complete');
  require('./get-outputs');
})().catch(e => { console.error(e.message); process.exit(1); });
