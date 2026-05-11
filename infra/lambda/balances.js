'use strict';

const { loadConfig, getClient, validateApiKey, ok, err } = require('./shared');

exports.handler = async (event) => {
  await loadConfig();
  if (!validateApiKey(event)) return err(401, 'Unauthorized');

  try {
    const { access_token, min_last_updated_datetime } = JSON.parse(event.body ?? '{}');
    if (!access_token) return err(400, 'access_token required');

    const options = {};
    if (min_last_updated_datetime) {
      options.min_last_updated_datetime = min_last_updated_datetime;
    } else {
      // Default to 30 days ago to satisfy institutions like Capital One (ins_128026) 
      // that require this field for real-time balance refreshes. 
      // 30 days is a safe default that usually returns the last known balance 
      // without forcing an expensive real-time sync if one isn't needed,
      // but satisfies the requirement that the field be present.
      const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
      options.min_last_updated_datetime = thirtyDaysAgo;
    }

    const resp = await getClient().accountsBalanceGet({ 
      access_token,
      options
    });
    return ok(resp.data);
  } catch (e) {
    console.error('balances:', e.response?.data ?? e.message);
    return err(500, 'Failed to fetch balances');
  }
};
