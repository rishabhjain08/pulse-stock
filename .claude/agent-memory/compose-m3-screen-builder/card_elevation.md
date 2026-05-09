---
name: Card elevation and surface tokens
description: Correct M3 tonal elevation for cards — use surfaceContainerLow + defaultElevation 0.dp (no drop shadow)
type: feedback
---

Use `containerColor = MaterialTheme.colorScheme.surfaceContainerLow` with `defaultElevation = 0.dp` for all cards. Never use `defaultElevation = 2.dp` (which produces a drop shadow, not a tonal lift).

**Why:** `defaultElevation` on M3 `Card` translates into a drop shadow, not the tonal elevation overlay. The visual differentiation from the background should come entirely from `surfaceContainerLow`'s surface tone relative to `background`. Drop shadows are a Material 2 pattern. Tonal differentiation in M3 is achieved via the container color token choice, not via elevation.

**How to apply:** Every `CreditCardSummaryCard`, `SplitwiseMonthCard`, `SplitwiseCard`, `InstitutionCard`, and `CategoryBreakdownCard` uses `surfaceContainerLow` + `defaultElevation = 0.dp`. Exception: `primaryContainer`-colored cards (e.g., highlighted ReconcileEntryCard) and `secondaryContainer` cards (AddBankRow) also use `0.dp` since the container color already provides chroma differentiation.
