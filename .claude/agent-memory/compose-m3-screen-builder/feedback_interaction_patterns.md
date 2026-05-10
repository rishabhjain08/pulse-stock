---
name: Interaction Pattern Preferences
description: User's confirmed interaction patterns — selection rules, toast guidance, FilterChip visual state, floating selected item
type: feedback
---

**Controls must live next to their affected data.** Never put a toggle or switch in a separate section away from the values it controls. Proximity is mandatory.

**Why:** Past designs separated controls from data, causing confusion about what the control affected.

**How to apply:** When placing any toggle, switch, or chip — check that it is in the same row or card as the values it changes.

---

**Never allow a null/empty selection state when a choice is required.** If the user tries to deselect their currently selected item, show a guidance toast ("Tap a different category to change it") instead of deselecting. There must always be exactly one selected item.

**Why:** Deselecting a required field left the UI in an invalid state that was silent and confusing.

**How to apply:** In any single-select list or chip group where a value is required, intercept the deselect gesture and emit a toast instead of clearing the selection.

---

**Use toasts for guidance, not blocking dialogs.** Auto-disappearing toasts (2–2.5 seconds) for hints and warnings. Implement as an inline Surface with `inverseSurface` container color, `slideInVertically` + `fadeIn` enter animation, `slideOutVertically` + `fadeOut` exit animation.

**Why:** Dialogs block the user; toasts inform without interrupting flow.

**How to apply:** Any hint, non-destructive warning, or guidance message uses this inline toast pattern. Reserve dialogs for destructive confirmations only.

---

**Selected item floats to top of lists; rest sorted alphabetically by display name.**

**Why:** The user's current pick should be instantly visible without scrolling.

**How to apply:** In any LazyColumn or list where items are selectable, partition: selected item(s) first, then remaining items sorted by displayName.

---

**FilterChips need explicit visual selection state.** Use `selectedContainerColor = MaterialTheme.colorScheme.primaryContainer` and a leading `Icons.Default.Check` icon so the selected state is unmistakable.

**Why:** Default M3 FilterChip selected state was too subtle; users could not tell what was selected.

**How to apply:** Any FilterChip in a single-select or multi-select group must use primaryContainer background + Check leading icon when selected.

---

**Consistent behavior regardless of data source.** No silent "read-only" states. If an action is unavailable, show a clear message. Never let a tap do nothing without explanation.
