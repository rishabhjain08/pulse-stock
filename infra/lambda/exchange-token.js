'use strict';

const { loadConfig, getClient, validateApiKey, ok, err } = require('./shared');

exports.handler = async (event) => {
  await loadConfig();
  if (!validateApiKey(event)) return err(401, 'Unauthorized');

  try {
    const { public_token } = JSON.parse(event.body ?? '{}');
    if (!public_token) return err(400, 'public_token required');

    const resp = await getClient().itemPublicTokenExchange({ public_token });
    return ok({ access_token: resp.data.access_token, item_id: resp.data.item_id });
  } catch (e) {
    console.error('exchange-token:', e.response?.data ?? e.message);
    return err(500, 'Failed to exchange token');
  }
};
