---
name: Unified CC section card pattern
description: Outer surfaceContainerLow card wraps inner surfaceContainerHigh sub-cards + dividers + totals + FilterChip for the Finances CC section
type: project
---

The Finances screen CC section uses a two-level card nesting pattern:

- **Outer card**: `surfaceContainerLow`, `shapes.large`, `defaultElevation = 0.dp`, `padding(12.dp)`, `verticalArrangement = spacedBy(8.dp)`. Rendered as a single `item {}` in the LazyColumn (replaces the previous flat sequence of items).
- **Inner sub-cards** (one per account): `surfaceContainerHigh`, `shapes.medium`, `defaultElevation = 0.dp`. This provides visible tonal lift above the outer card without a drop shadow.
- Inside the outer card: sub-cards → `HorizontalDivider` → `CreditCardTotalsRow` (bare Row, no Card wrapper) → optional second `HorizontalDivider` + `FilterChip` if Splitwise connected.

**Business CC cards** (`usesWindowHeuristic = true`, defined as `lastStatementDate == null && nextDueDate == null`):
- Show only the "Current" column — Statement and Due Date columns are hidden via `if (!isBusinessCard)` guards.
- No badge or qualifier text on the card itself — the absence of the columns is the signal.
- Extension property `AccountEntity.usesWindowHeuristic` lives in `PoarVaultModels.kt`.

**Totals row**: Two columns only (Statement, Current). Statement column hidden when no account has `statementBalance != null`. No third empty column. `reimbursable` always uses `currentMonthReimbursable` (not the browsed Splitwise month).

**FilterChip label**: "Subtract Splitwise reimbursable" (not "Subtract Splitwise").

**Why**: Groups all CC data into one visual unit, matches M3 tonal hierarchy, avoids scattered LazyColumn items that felt disconnected.

**How to apply**: When adding new CC-level summary data, put it inside the outer card Column before or after the HorizontalDivider/totals block. Do not add new top-level LazyColumn items for CC-scoped data.
