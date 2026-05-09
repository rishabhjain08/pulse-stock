---
name: CategoryRow emoji pill and count chip pattern
description: CategoryRow in FinancesScreen uses a Box-clipped emoji pill and a Surface chip for the transaction count
type: project
---

`CategoryRow` (inside `CategoryBreakdownCard`) uses two visual enhancements over plain text:

1. **Emoji pill** — a 36dp × 36dp `Box` clipped to `RoundedCornerShape(8.dp)` with `background(MaterialTheme.colorScheme.surfaceContainerHighest)` contains the emoji glyph. This gives the emoji a subtle tonal backing that differentiates it from the plain text content around it. The emoji `Text` carries no `contentDescription` (decorative — meaning conveyed by adjacent `displayName` text).

2. **Transaction count chip** — a `Surface` with `shape = MaterialTheme.shapes.extraSmall` and `color = MaterialTheme.colorScheme.secondaryContainer` wraps the `"Nx"` count label. This converts the plain `labelSmall` count into a chip with proper containment and secondary tonal color, improving visual hierarchy over just `onSurfaceVariant` text.

**Why:** The original implementation used a plain `Text(meta.emoji)` with `Modifier.width(32.dp)` — no background context. On a light theme the emoji floats without a container, making it hard to scan. The pill provides a consistent anchoring shape. The count chip makes the "how many transactions" info scannable at a glance without competing with the amount.

**How to apply:** Any future list row that pairs an emoji glyph with a count should follow this pill + chip pattern. Use `surfaceContainerHighest` for the emoji pill background (one step above `surfaceContainerHigh` to ensure the pill reads against `surfaceContainerLow` cards).
