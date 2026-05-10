#!/usr/bin/env node
'use strict';

// Update one or more SSM secrets from infra/.env without touching infrastructure.
// Usage:
//   node infra/scripts/update-secrets.js                  # update all secrets
//   node infra/scripts/update-secrets.js splitwise        # update only Splitwise credentials
//   node infra/scripts/update-secrets.js plaid            # update only Plaid credentials

const { loadEnv, putParam } = require('./_common');

const ALL_PARAMS = {
  plaid: [
    ['/poarvault/plaid-client-id', 'PLAID_CLIENT_ID'],
    ['/poarvault/plaid-secret',    'PLAID_SECRET'],
    ['/poarvault/plaid-env',       'PLAID_ENV'],
  ],
  splitwise: [
    ['/poarvault/splitwise-consumer-key',    'SPLITWISE_CONSUMER_KEY'],
    ['/poarvault/splitwise-consumer-secret', 'SPLITWISE_CONSUMER_SECRET'],
  ],
};

(async () => {
  loadEnv();

  const filter = process.argv[2]?.toLowerCase();
  const groups = filter
    ? Object.entries(ALL_PARAMS).filter(([k]) => k === filter)
    : Object.entries(ALL_PARAMS);

  if (groups.length === 0) {
    console.error(`Unknown group: ${filter}. Valid options: ${Object.keys(ALL_PARAMS).join(', ')}`);
    process.exit(1);
  }

  for (const [group, params] of groups) {
    console.log(`\n==> ${group}`);
    for (const [ssmName, envKey] of params) {
      const value = process.env[envKey];
      if (!value) { console.warn(`  SKIP ${ssmName} — ${envKey} not set in infra/.env`); continue; }
      await putParam(ssmName, value);
    }
  }

  console.log('\nDone. Lambda picks up new values on its next invocation.');
})();
