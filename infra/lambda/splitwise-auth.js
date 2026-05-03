'use strict';

const { SSMClient, GetParameterCommand } = require('@aws-sdk/client-ssm');

const ssm = new SSMClient({ region: process.env.AWS_REGION });
let cfg = null;

const json = (status, body) => ({
  statusCode: status,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

async function getParam(name) {
  const { Parameter } = await ssm.send(
    new GetParameterCommand({ Name: name, WithDecryption: true })
  );
  return Parameter.Value;
}

async function getConfig() {
  if (cfg) return cfg;
  const [apiKey, consumerKey, consumerSecret] = await Promise.all([
    getParam('/poarvault/api-key'),
    getParam('/poarvault/splitwise-consumer-key'),
    getParam('/poarvault/splitwise-consumer-secret'),
  ]);
  return (cfg = { apiKey, consumerKey, consumerSecret });
}

exports.handler = async (event) => {
  const config = await getConfig();
  if ((event.headers ?? {})['x-api-key'] !== config.apiKey)
    return json(401, { error: 'Unauthorized' });

  const { code } = JSON.parse(event.body ?? '{}');
  if (!code) return json(400, { error: 'code required' });

  try {
    const resp = await fetch('https://secure.splitwise.com/oauth/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        grant_type: 'authorization_code',
        client_id: config.consumerKey,
        client_secret: config.consumerSecret,
        code,
        redirect_uri: 'pulsestock://splitwise/callback',
      }),
    });

    if (!resp.ok) {
      // Do not log the response body — it may contain partial token info
      console.error('splitwise token exchange failed, status:', resp.status);
      return json(502, { error: 'Token exchange failed' });
    }

    const { access_token } = await resp.json();
    // Return only the token. Lambda stores nothing — token lives on device only.
    return json(200, { access_token });
  } catch (e) {
    console.error('splitwise-auth error:', e.message);
    return json(500, { error: 'Internal error' });
  }
};
