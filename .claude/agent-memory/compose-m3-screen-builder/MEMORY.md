# Agent Memory — Compose M3 Screen Builder

- [Card elevation & surface tokens](card_elevation.md) — surfaceContainerLow + defaultElevation 0.dp; no drop shadow; tonal lift comes from container color only
- [Touch target pattern](touch_targets.md) — Never constrain IconButton with .size(32dp/36dp); M3 IconButton is 48dp by default
- [Entrance animation pattern](entrance_animation.md) — AnimatedVisibility(fadeIn+slideInVertically, initialOffsetY={it/10}) on LazyColumn; contentVisible via LaunchedEffect(Unit)
- [Bottom sheet pattern](bottom_sheets.md) — dragHandle explicit, containerColor=surfaceContainerHigh, content uses verticalScroll (no nested LazyColumn)
- [animateContentSize import](animatecontentsize_import.md) — Import from androidx.compose.animation (NOT animation.core)
- [Card shape standard](card_shapes.md) — shapes.medium (12dp) for list-level cards; shapes.large (16dp) for section container cards
- [Interactive card pattern](interactive_cards.md) — Use Card(onClick=...) not Card+Modifier.clickable for proper M3 ripple/state-layer
- [CategoryRow emoji pill + count chip](category_row_pattern.md) — 36dp Box+clip emoji pill (surfaceContainerHighest bg), Surface chip for Nx count (secondaryContainer)
