---
name: Card shape standard — medium for list, large for section
description: shapes.medium (12dp) for list-level cards, shapes.large (16dp) for section containers in PulseStock
type: feedback
---

PulseStock uses a two-tier card shape convention:
- `MaterialTheme.shapes.medium` (12dp) — list-level cards: individual account rows, reconcile entry row, AddBankRow CTA.
- `MaterialTheme.shapes.large` (16dp) — section container cards: SplitwiseCard, SplitwiseMonthCard, CategoryBreakdownCard, InstitutionCard. These contain multiple child elements and act as a visual grouping container.

**Why:** The distinction is functional — section cards group related content (e.g., an institution with multiple account rows, a month overview with month-nav controls). Larger corner radius helps the eye perceive them as distinct containers rather than list items. List-level cards are more compact and benefit from the smaller radius.

**How to apply:** Ask "does this card contain sub-rows or multiple distinct sub-composables?" If yes → `shapes.large`. If it's a single-purpose row-level card → `shapes.medium`. Never use a hardcoded `RoundedCornerShape(Xdp)` — always use theme shape tokens so the design system can override globally.
