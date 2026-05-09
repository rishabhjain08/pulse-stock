#!/usr/bin/env node
'use strict';

// Rotates the PoarVault API key:
//   1. Generates a new random 64-char hex key
//   2. Overwrites /poarvault/api-key in SSM
//   3. Touches each Lambda function's description to force cold starts
//      (flushes the module-level cache in shared.js that holds the old key)
//   4. Prints the new key so you can update local.properties

const crypto = require('crypto');
const { execSync } = require('child_process');
const { loadEnv, putParam } = require('./_common');

const FUNCTION_NAMES = [
  'poarvault-link-token',
  'poarvault-exchange-token',
  'poarvault-balances',
  'poarvault-transactions',
  'poarvault-disconnect',
  'poarvault-splitwise-auth',
  'poarvault-liabilities',
];

(async () => {
  loadEnv();

  const newKey = crypto.randomBytes(32).toString('hex');
  console.log('==> Updating SSM /poarvault/api-key');
  await putParam('/poarvault/api-key', newKey);

  // Force cold starts so warm Lambda containers flush their cached key.
  console.log('==> Forcing Lambda cold starts via AWS CLI');
  const env = {
    ...process.env,
    AWS_ACCESS_KEY_ID:     process.env.AWS_ACCESS_KEY_ID,
    AWS_SECRET_ACCESS_KEY: process.env.AWS_SECRET_ACCESS_KEY,
    AWS_DEFAULT_REGION:    process.env.AWS_REGION,
  };
  const desc = `key-rotated-${Date.now()}`;
  for (const name of FUNCTION_NAMES) {
    execSync(
      `aws lambda update-function-configuration --function-name ${name} --description "${desc}"`,
      { env, stdio: 'pipe' }
    );
    console.log(`    ${name}: cold-start forced`);
  }

  console.log('');
  console.log('==> Done. Update local.properties with:');
  console.log(`    POARVAULT_API_KEY=${newKey}`);
  console.log('');
})().catch(e => { console.error(e.message); process.exit(1); });
