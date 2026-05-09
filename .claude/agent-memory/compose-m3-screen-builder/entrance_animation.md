---
name: Entrance animation pattern
description: Standard LazyColumn entrance animation for PulseStock tab screens
type: feedback
---

Wrap the `LazyColumn` (or equivalent scrollable content) in `AnimatedVisibility` triggered immediately on composition.

**Pattern:**
```kotlin
var contentVisible by remember { mutableStateOf(false) }
LaunchedEffect(Unit) { contentVisible = true }

AnimatedVisibility(
    visible = contentVisible,
    enter = fadeIn() + slideInVertically(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        initialOffsetY = { it / 10 }, // subtle: 10% of content height
    ),
) { LazyColumn(...) }
```

**Why:** `initialOffsetY = { it / 10 }` (10% offset) is intentionally subtle — it reads as polish rather than distraction. Full-height slide (`{ it }`) feels heavy on a content-dense finance screen.

**How to apply:** Add to every new tab-level screen. The `LaunchedEffect(Unit)` fires immediately after composition, so there is no perceptible delay — the animation runs on first frame.
