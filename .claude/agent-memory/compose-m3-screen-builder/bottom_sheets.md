---
name: Bottom sheet pattern
description: ModalBottomSheet conventions — drag handle, surfaceContainerHigh container, verticalScroll content
type: feedback
---

Every `ModalBottomSheet` in PulseStock must follow these rules:

1. **Drag handle** — always pass `dragHandle = { BottomSheetDefaults.DragHandle() }` explicitly.

2. **Container color** — use `containerColor = MaterialTheme.colorScheme.surfaceContainerHigh` so the sheet lifts above the page background. Do not leave the default `surface`.

3. **Scrollable content** — sheet content `Column` must use `Modifier.verticalScroll(rememberScrollState())` instead of a nested `LazyColumn`. Nested lazy layouts inside `ModalBottomSheet` cause height measurement crashes.

4. **Sheet title treatment** — `titleLarge` + `FontWeight.SemiBold` in `onSurface`. Subtitle uses `bodyMedium` in `onSurfaceVariant`. A `HorizontalDivider` separates the header from the list content.

5. **Bottom padding** — always `.padding(bottom = 32.dp)` at the end of content to clear the navigation bar.

**Why:** The drill-down and picker sheets previously used an extra `Surface` wrapper around the title row and had no `verticalScroll`, clipping content on small screens. `surfaceContainerHigh` makes the sheet visually distinct from the page background. The `dragHandle` must be explicit — it was silently omitted in some BOM versions.

**How to apply:**
```kotlin
ModalBottomSheet(
    onDismissRequest = vm::closeSheet,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Text("Sheet title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Subtitle", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(...)
        // content rows...
    }
}
```
