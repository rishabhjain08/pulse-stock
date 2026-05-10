# Agent Memory — Compose M3 Screen Builder

- [Card elevation & surface tokens](card_elevation.md) — surfaceContainerLow + defaultElevation 0.dp; no drop shadow; tonal lift comes from container color only
- [Touch target pattern](touch_targets.md) — Never constrain IconButton with .size(32dp/36dp); M3 IconButton is 48dp by default
- [Entrance animation pattern](entrance_animation.md) — AnimatedVisibility(fadeIn+slideInVertically, initialOffsetY={it/10}) on LazyColumn; contentVisible via LaunchedEffect(Unit)
- [Bottom sheet pattern](bottom_sheets.md) — dragHandle explicit, containerColor=surfaceContainerHigh, content uses verticalScroll (no nested LazyColumn)
- [animateContentSize import](animatecontentsize_import.md) — Import from androidx.compose.animation (NOT animation.core)
- [Card shape standard](card_shapes.md) — shapes.medium (12dp) for list-level cards; shapes.large (16dp) for section container cards
- [Interactive card pattern](interactive_cards.md) — Use Card(onClick=...) not Card+Modifier.clickable for proper M3 ripple/state-layer
- [CategoryRow emoji pill + count chip](category_row_pattern.md) — 36dp Box+clip emoji pill (surfaceContainerHighest bg), Surface chip for Nx count (secondaryContainer)
- [Visual design preferences](feedback_visual_design.md) — Spacious layouts, strict M3 color roles, emoji anchors in category rows, no clutter
- [Interaction pattern preferences](feedback_interaction_patterns.md) — Controls next to data, no null selection (toast instead), toast guidance, selected item floats top, FilterChip explicit state
- [Dialog design preferences](feedback_dialog_patterns.md) — Yes/No framing, question-title, dismiss=No (safe default), destructive shows count + error color
- [Data display preferences](feedback_data_display.md) — Spinner not $0 while loading, — for missing, temporal alignment, sync on Accounts tab only
- [Navigation & architecture preferences](feedback_navigation_architecture.md) — Drill-through uses IN (all sibling PFC codes), ViewModel groupBy displayName, allCodes in state
- [Anti-patterns to avoid](feedback_anti_patterns.md) — No FAB on Finances, no remove-override button, no auto-assign on Add, no raw PFC codes in UI
- [Unified CC card pattern](unified_cc_card_pattern.md) — Outer surfaceContainerLow/shapes.large wraps inner surfaceContainerHigh sub-cards + divider + totals + FilterChip
- [History stacked bar chart pattern](history_chart_pattern.md) — Canvas+horizontalScroll, fixed Y-axis outside scroll, null=all filter semantics, merchant data via getMonthlySpendingHistoryWithRaw
