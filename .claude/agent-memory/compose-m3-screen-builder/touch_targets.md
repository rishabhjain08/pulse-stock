---
name: Touch target pattern for IconButton
description: Never override IconButton size to sub-48dp; M3 handles the minimum touch target internally
type: feedback
---

Never apply `.size(32.dp)` or `.size(36.dp)` to `IconButton`. M3 `IconButton` already enforces a 48dp×48dp minimum touch target internally.

**Why:** Previous screens used `Modifier.size(32.dp)` on `IconButton` (e.g., SplitwiseMonthCard month nav arrows, InstitutionCard sync/disconnect). This shrinks the clickable area below the 48dp WCAG AA minimum, causing accessibility failures and poor tap ergonomics on small screens.

**How to apply:** Only control the *icon inside* with `Modifier.size(18.dp)` or `Modifier.size(20.dp)`. Let the `IconButton` wrapper retain its default 48dp×48dp touch target. If you need semantics on the button, use `Modifier.semantics { contentDescription = ... }` on the `IconButton` itself (not inside the Icon).
