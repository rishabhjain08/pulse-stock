---
name: Data Display Preferences
description: User's confirmed data display rules — no stale values, spinner while loading, temporal alignment, account-level sync
type: feedback
---

**Never show stale or potentially-wrong values.** Show a spinner while data is loading. Show `—` (em dash) for genuinely missing data. Showing $0 while data has not yet loaded is unacceptable — it looks like the user has no money.

**Why:** $0 displayed during a load state caused real confusion and loss of trust in the app.

**How to apply:** Every monetary or count value must have a tri-state: loading (spinner), loaded (value), missing (—). Never default to 0 as the initial display state.

---

**Temporal alignment before wiring two data sources.** Before combining values from different sources (e.g. current credit card balance + reimbursable total), confirm both cover the same time period. Current CC balance must pair with current-month reimbursable, not the last-browsed month.

**Why:** A past bug showed the wrong month's reimbursable next to the current balance, making the math misleading.

**How to apply:** When writing a ViewModel that combines two time-scoped values, explicitly document which time window each covers and assert they match.

---

**Sync is account-level only.** One sync button on the Accounts tab covers Plaid + Splitwise. Data views (Finances, etc.) auto-load on ViewModel init and must never show their own sync button.

**Why:** Duplicate sync entry points caused confusion about which one to use and whether they had different effects.

**How to apply:** If a non-Accounts screen needs fresh data, trigger it via ViewModel init (auto-load) or observe a shared flow. Never add a sync FAB or button to Finances, Spending, or any data-view screen.
