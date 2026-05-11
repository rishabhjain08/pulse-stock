---
name: Stacked Clean CategoryRow Pattern
description: A professional, two-line category row with a larger emoji pill and stacked meta/amount details.
type: project
---

`CategoryRow` (inside `CategoryBreakdownCard`) uses a "Stacked Clean" professional layout:

1. **Larger Emoji Pill** — A 40dp × 40dp `Box` clipped to `RoundedCornerShape(10.dp)` with `surfaceContainerHighest` background. The emoji uses `titleMedium` for better prominence and scans better than floating icons.

2. **Stacked Meta (Center)** — Category name (`bodyMedium`, `FontWeight.Medium`) stacked above the transaction count (`bodySmall`, `onSurfaceVariant`). This provides clear identity while tucking secondary details below the primary label.

3. **Stacked Financials (Right)** — Monospaced amount (`bodyMedium`, `SemiBold`) stacked above the percentage of total spend (`bodySmall`, `onSurfaceVariant`). Aligning these to the end creates a clean vertical gutter.

**Why:** The previous "chip" based design felt fragmented. This two-line approach is standard in high-end financial apps (like Apple Card or Monarch). It allows for more data density (adding percentages) without visual clutter.

**How to apply:** 
- Use `40.dp` for the icon container.
- Use `Column(modifier = Modifier.weight(1f))` for the middle labels.
- Add a `16.dp` horizontal `Spacer` between the label and amount columns to prevent visual crowding.
- Always include `totalSpend: Double` to calculate and display the percentage.
