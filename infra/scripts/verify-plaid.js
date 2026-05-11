'use strict';

const { Configuration, PlaidApi, PlaidEnvironments, CountryCode, Products } = require('plaid');
const fs = require('fs');
const path = require('path');
const dotenv = require('dotenv');

// Load from infra/.env
const envPath = path.resolve(__dirname, 'infra', '.env');
if (!fs.existsSync(envPath)) {
  console.error('infra/.env not found');
  process.exit(1);
}
dotenv.config({ path: envPath });

const clientId = process.env.PLAID_CLIENT_ID;
const secret = process.env.PLAID_SECRET;
const plaidEnv = process.env.PLAID_ENV || 'production';

console.log(`==> Verifying Plaid Credentials`);
console.log(`    Client ID: ${clientId}`);
console.log(`    Secret:    ${secret ? '********' + secret.slice(-4) : 'MISSING'}`);
console.log(`    Env:       ${plaidEnv}`);
console.log('');

const configuration = new Configuration({
  basePath: PlaidEnvironments[plaidEnv],
  baseOptions: {
    headers: {
      'PLAID-CLIENT-ID': clientId,
      'PLAID-SECRET': secret,
    },
  },
});

const client = new PlaidApi(configuration);

(async () => {
  try {
    console.log('--> Attempting link/token/create...');
    const response = await client.linkTokenCreate({
      user: { client_user_id: 'test-user-id' },
      client_name: 'PoarVault Test',
      products: [Products.Transactions],
      country_codes: [CountryCode.Us],
      language: 'en',
    });
    console.log('✅ SUCCESS!');
    console.log('   Link Token:', response.data.link_token);
  } catch (error) {
    console.log('❌ FAILED');
    if (error.response) {
      console.log('   Status:', error.response.status);
      console.log('   Error Code:', error.response.data.error_code);
      console.log('   Error Message:', error.response.data.error_message);
      console.log('   Request ID:', error.response.data.request_id);
    } else {
      console.log('   Error:', error.message);
    }
    process.exit(1);
  }
})();
