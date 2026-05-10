---
name: Visual Design Preferences
description: User's confirmed visual design preferences — spacious layouts, strict M3, emoji anchors, no clutter
type: feedback
---

Use clean, big, spacious layouts with generous padding and large touch targets (48dp minimum). Never pack elements tightly.

Apply Material 3 strictly: use M3 color roles (primaryContainer, surfaceContainerHighest, secondaryContainer, etc.), tonal elevation, and M3 shapes. No hardcoded hex colors, no Material 2 imports.

Every UI element must earn its place — remove redundant controls without hesitation. If two elements convey the same information or trigger the same action, eliminate one.

Use emoji as visual anchors in category lists and financial rows. They help the user scan quickly and are a deliberate design choice, not decoration to be removed.

**Why:** Derived from many rounds of real feedback where cluttered layouts and small targets caused friction. The user explicitly confirmed these preferences.

**How to apply:** Default to 16dp+ content padding, 48dp touch targets, emoji in any row that has a category or financial label, and strip any element that duplicates another.
