---
name: Navigation and Architecture Preferences
description: User's confirmed navigation/architecture rules — drill-through uses IN query, ViewModel deduplication of display names
type: feedback
---

**Spending breakdown drill-through must use IN (...) not = for Plaid PFC codes.** When multiple Plaid PFC codes map to the same display name (e.g. ENTERTAINMENT_GYMS and PERSONAL_CARE_GYMS both display as "Gym"), the drill-through SQL/query must use `IN (code1, code2, ...)` to fetch all matching transactions.

**Why:** Using `= code1` silently excluded transactions tagged with sibling codes, making the drill-through total disagree with the breakdown total.

**How to apply:** At the ViewModel collect site, groupBy displayName and store all sibling PFC codes per canonical display name in the UI state. The drill-through destination receives the full list of codes and queries with IN.

---

**Breakdown merges synonymous display names at the ViewModel.** Deduplication happens at the collect/groupBy site, not in the UI. The UI state holds: displayName, totalAmount, allCodes (list of every PFC code that maps to this name).

**Why:** Duplicated display names in the breakdown list confused users into thinking they had two separate categories.

**How to apply:** In any spending/category breakdown ViewModel, after fetching rows, groupBy { it.displayName } and sum amounts. The resulting state object carries allCodes so drill-through can use them.
