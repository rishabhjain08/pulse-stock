'use strict';

const { SSMClient, GetParameterCommand } = require('@aws-sdk/client-ssm');
const { Configuration, PlaidApi, PlaidEnvironments } = require('plaid');

const ssm = new SSMClient({ region: process.env.AWS_REGION });

// Module-level cache — survives across warm invocations of the same container.
let plaidClient = null;
let apiKey = null;

async function getParam(name, withDecryption = true) {
  const { Parameter } = await ssm.send(
    new GetParameterCommand({ Name: name, WithDecryption: withDecryption })
  );
  return Parameter.Value;
}

async function loadConfig() {
  if (plaidClient && apiKey) return;
  const [clientId, secret, plaidEnv, key] = await Promise.all([
    getParam('/poarvault/plaid-client-id'),
    getParam('/poarvault/plaid-secret'),
    getParam('/poarvault/plaid-env', false),
    getParam('/poarvault/api-key'),
  ]);
  apiKey = key;
  plaidClient = new PlaidApi(
    new Configuration({
      basePath: PlaidEnvironments[plaidEnv] ?? PlaidEnvironments.sandbox,
      baseOptions: {
        headers: {
          'PLAID-CLIENT-ID': clientId,
          'PLAID-SECRET': secret,
        },
      },
    })
  );
}

function validateApiKey(event) {
  const provided = (event.headers ?? {})['x-api-key'];
  return typeof provided === 'string' && provided === apiKey;
}

function ok(body) {
  return {
    statusCode: 200,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}

function err(status, message) {
  return {
    statusCode: status,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ error: message }),
  };
}

function getClient() {
  return plaidClient;
}

module.exports = { loadConfig, getClient, validateApiKey, ok, err };
