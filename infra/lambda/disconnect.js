'use strict';

const { loadConfig, getClient, validateApiKey, ok, err } = require('./shared');

exports.handler = async (event) => {
  await loadConfig();
  if (!validateApiKey(event)) return err(401, 'Unauthorized');

  try {
    const { access_token } = JSON.parse(event.body ?? '{}');
    if (!access_token) return err(400, 'access_token required');

    await getClient().itemRemove({ access_token });
    return ok({ removed: true });
  } catch (e) {
    console.error('disconnect:', e.response?.data ?? e.message);
    return err(500, 'Failed to disconnect');
  }
};
