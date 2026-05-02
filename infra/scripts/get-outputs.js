#!/usr/bin/env node
'use strict';

// Print the API URL and API key after a successful deploy.

const { loadEnv, getStackOutput, getParam } = require('./_common');

(async () => {
  loadEnv();

  const [apiUrl, apiKey] = await Promise.all([
    getStackOutput('poarvault', 'ApiUrl'),
    getParam('/poarvault/api-key'),
  ]);

  console.log('');
  console.log('┌─ PoarVault API ──────────────────────────────────────────────────────┐');
  console.log(`│  URL:     ${apiUrl}`);
  console.log(`│  API Key: ${apiKey}`);
  console.log('└──────────────────────────────────────────────────────────────────────┘');
  console.log('');
  console.log('Add to local.properties:');
  console.log(`  POARVAULT_API_URL=${apiUrl}`);
  console.log(`  POARVAULT_API_KEY=${apiKey}`);
})().catch(e => { console.error(e.message); process.exit(1); });
