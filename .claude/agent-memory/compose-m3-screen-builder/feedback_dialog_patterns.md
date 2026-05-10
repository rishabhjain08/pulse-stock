---
name: Dialog Design Preferences
description: User's confirmed dialog patterns — Yes/No framing, destructive confirmations with count, dismiss = safe default
type: feedback
---

**Frame binary decisions as Yes/No questions.** Title the dialog as a question (e.g. "Apply to all Merchant?"). Label buttons "Yes" and "No" as TextButtons. Dismissing by tapping outside equals "No" — the safe default.

**Why:** Labels like "Just this one" / "Apply to all" as the only options are ambiguous and have caused user errors.

**How to apply:** Any confirmation dialog with two choices uses a question title + "Yes" / "No" TextButton labels. Never use action-phrase-only labels as the sole options.

---

**Destructive confirmation dialogs must show the count of affected items.** Example: "3 transactions will revert to their default category." The confirm button uses `error` container color to signal danger.

**Why:** Users need to understand the blast radius before confirming a destructive action.

**How to apply:** Before showing a destructive dialog, resolve the count of affected records and embed it in the dialog body. Confirm button = `ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)`.
