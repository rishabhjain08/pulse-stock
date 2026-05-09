---
name: animateContentSize import path
description: Correct import for Modifier.animateContentSize() — commonly mis-imported from animation.core
type: feedback
---

`animateContentSize` is a `Modifier` extension. Import from `androidx.compose.animation`, NOT from `androidx.compose.animation.core`.

**Why:** `animation.core` contains `AnimationSpec` types like `spring()`, `tween()` etc. `animateContentSize` is in the higher-level `animation` layout modifier package. Wrong import causes an unresolved reference compile error.

**How to apply:**
```kotlin
import androidx.compose.animation.animateContentSize      // CORRECT
// import androidx.compose.animation.core.animateContentSize  // WRONG — compile error
```
