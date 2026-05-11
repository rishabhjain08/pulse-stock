'use strict';

const { SSMClient, GetParameterCommand } = require('@aws-sdk/client-ssm');
const { loadEnv, cfg } = require('./_common');

(async () => {
  loadEnv();
  const ssm = new SSMClient(cfg());

  const names = [
    '/poarvault/plaid-client-id',
    '/poarvault/plaid-secret',
    '/poarvault/plaid-env'
  ];

  for (const name of names) {
    const { Parameter } = await ssm.send(new GetParameterCommand({ Name: name, WithDecryption: true }));
    let val = Parameter.Value;
    if (name.includes('secret')) val = val.substring(0, 5) + '...';
    console.log(`${name}: ${val}`);
  }

})().catch(e => { console.error(e.message); process.exit(1); });
