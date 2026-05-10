---
name: History stacked bar chart pattern
description: SpendingHistorySheet stacked bar chart impl: Canvas in horizontalScroll, Y-axis fixed outside scroll, tooltip as Compose Surface overlay, null=all filter semantics
type: project
---

## Stacked bar chart in SpendingHistorySheet

**Pattern:** `Box(fillMaxWidth)` with a fixed Y-axis `Box` (44dp wide, non-scrolling) and a `horizontalScroll` chart area side-by-side. The Y-axis is a separate `Canvas` drawn outside the scroll, so labels stay visible while the chart scrolls.

**Bar layout:** 48dp wide, 10dp gap, chart Canvas height 160dp. Pre-scrolled to rightmost (newest) month via `LaunchedEffect(orderedHistory.size) { scrollState.scrollTo(scrollState.maxValue) }`.

**Y-axis labels and gridlines:** Use `nativeCanvas.drawText` inside Canvas for precise pixel-positioned text (no `BasicText` in drawscope). Labels formatted as `formatYLabel` ($1k, $2.5k, etc.). Grid values computed via `buildGridValues` — 3-4 round steps up to max.

**Color map pattern:** `buildHistoryColorMap(allCategories, selectedCategories, palette, miscColor)` — top-5 get palette colors, rest grouped into "Misc" (outlineVariant color) when no filter active. Filter resets palette assignment fresh.

**Segment builder:** `buildBarSegments(...)` pure function — returns `List<BarSegment>`. When merchant filter is active, switches to merchant-keyed grouping from `MonthlyMerchantHistory`.

**Tooltip:** Local `rememberSaveable { mutableStateOf<Int?>(null) }` index only. `AnimatedVisibility(fadeIn/fadeOut)` over a `Surface(shapes.small, surfaceContainerHighest)` positioned via `Modifier.offset(x=barLeftDp)`. NOT in ViewModel.

**Tap detection:** `detectTapGestures` inside `pointerInput(orderedHistory, ...)` — guards that tap is within bar width, not the gap.

## null = all filter semantics

- `null` in `historySelectedCategories` / `historySelectedMerchants` = all selected (default, no filter active)
- `emptySet()` = user explicitly cleared everything → chart shows empty bars
- Dialog initializes with `selectedCategories ?: allKeys` so all boxes appear checked when null
- On confirm: `if (checkedKeys == allKeys) null else if (checkedKeys.isEmpty()) emptySet() else checkedKeys`

## Merchant data sourcing

`getMonthlySpendingHistoryWithRaw(accountIds)` returns `Pair<List<MonthlySpendRow>, List<PlaidTransaction>>` in one DB fetch. The ViewModel calls `aggregateMerchantHistory(rawTxns)` to derive both `MonthlyMerchantHistory` (per month, per merchant totals) and `MerchantSpendSummary` (12-month aggregate + primary category = mode of effectiveCategory across all txns for that merchant).

## Smart merchant list filtering

When `historySelectedCategories` is non-null in the merchant dialog, the displayed list is pre-filtered to merchants whose `primaryCategory` is in the selected category set.

**Why:** User flow is categories-first → merchant drill-down. Showing all merchants when a category filter is active would be confusing.

**How to apply:** `visibleMerchants = if (historySelectedCategories == null) allMerchants else allMerchants.filter { it.primaryCategory in historySelectedCategories }` in the merchant dialog composable.
