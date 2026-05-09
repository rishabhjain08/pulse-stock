---
name: Interactive card pattern
description: Use Card(onClick=...) not Card+Modifier.clickable for proper M3 ripple/state-layer
type: feedback
---

For tappable cards, use `Card(onClick = { ... })` rather than `Card(modifier = Modifier.clickable { ... })`.

**Why:** `Modifier.clickable` bypasses the M3 `Card` interactive state layer (pressed, hovered, focused overlays). `Card(onClick=...)` wires into M3's `Indication` system and applies the correct ripple color derived from `onSurface` at the specified alpha — exactly what Material 3 spec requires. The `AddBankRow` card was the specific offender in PulseStock.

**How to apply:** Whenever a card's entire surface is the touch target, pass `onClick` to `Card(...)` directly. If only a sub-region is tappable, keep a plain `Card` and use `Modifier.clickable` with `onClickLabel` for accessibility on the inner row.
