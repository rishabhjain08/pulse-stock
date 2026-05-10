---
name: Specific Anti-Patterns to Avoid
description: Explicit anti-patterns confirmed by the user — FAB on Finances, separate remove-override button, auto-assign on Add, raw PFC codes
type: feedback
---

**No FAB on the Finances screen.** It was removed because it disrupts scrolling on a data-heavy screen.

**Why:** A floating button over a scroll area obscured content and had no clear primary action to justify the visual weight.

**How to apply:** Never re-add a FAB to the Finances or any spending/data-view screen. Contextual actions belong in row-level menus or top-bar actions.

---

**No separate "Remove override" button.** The category deselect gesture (now a toast) replaced it. There is no standalone button to remove a category override.

**Why:** A dedicated remove button added visual clutter and duplicated the gesture. Now deselecting shows a toast instead of removing.

**How to apply:** If a transaction has an overridden category, the only way to change it is to pick a different category. Do not add a "clear" or "remove override" affordance.

---

**No auto-assigning when user types and clicks "Add" for a custom category.** Clicking Add adds the item to the list only. The user must explicitly tap/select it to assign it. Never auto-select the newly added item.

**Why:** Auto-assigning surprised users who wanted to add first and decide later.

**How to apply:** In any "add custom item" flow, the Add action inserts the item into the list in unselected state. Selection is a separate, explicit gesture.

---

**Never show raw Plaid PFC codes to the user.** Strings like "FOOD_AND_DRINK_GROCERIES" or "ENTERTAINMENT_GYMS" must never appear in any UI text. Always resolve to a friendly display name via CategoryMeta before rendering.

**Why:** Raw codes are meaningless to end users and break the illusion of a polished app.

**How to apply:** Any composable that renders a category name must receive a resolved displayName string, not a PFC code. The resolution happens in the ViewModel or repository layer.
