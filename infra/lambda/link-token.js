'use strict';

const { loadConfig, getClient, validateApiKey, ok, err } = require('./shared');
const { CountryCode, Products } = require('plaid');
// Balance is always available automatically (not a product).
// Liabilities enables /liabilities/get for statement balance + due date on credit cards.
// Note: existing items linked without Liabilities will need to be re-linked by the user.

exports.handler = async (event) => {
  await loadConfig();
  if (!validateApiKey(event)) return err(401, 'Unauthorized');

  try {
    const { user_id } = JSON.parse(event.body ?? '{}');
    if (!user_id) return err(400, 'user_id required');

    const resp = await getClient().linkTokenCreate({
      user: { client_user_id: user_id },
      client_name: 'PoarVault',
      products: [Products.Transactions, Products.Liabilities],
      country_codes: [CountryCode.Us],
      language: 'en',
      android_package_name: 'com.pulsestock.app',
    });
    return ok({ link_token: resp.data.link_token });
  } catch (e) {
    console.error('link-token:', e.response?.data ?? e.message);
    return err(500, 'Failed to create link token');
  }
};
