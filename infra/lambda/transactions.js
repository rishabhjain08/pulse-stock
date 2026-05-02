'use strict';

const { loadConfig, getClient, validateApiKey, ok, err } = require('./shared');

exports.handler = async (event) => {
  await loadConfig();
  if (!validateApiKey(event)) return err(401, 'Unauthorized');

  try {
    const { access_token, start_date, end_date } = JSON.parse(event.body ?? '{}');
    if (!access_token || !start_date || !end_date)
      return err(400, 'access_token, start_date, end_date required');

    const resp = await getClient().transactionsGet({
      access_token,
      start_date,
      end_date,
      options: { count: 500 },
    });
    return ok(resp.data);
  } catch (e) {
    console.error('transactions:', e.response?.data ?? e.message);
    return err(500, 'Failed to fetch transactions');
  }
};
