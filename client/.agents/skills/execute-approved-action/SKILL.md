---
name: execute-approved-action
description: Execute a previously approved Work Overview action only after explicit final approval.
---

# Execute approved action

Use `execute_approved_action` only after the exact draft was explicitly approved and the user has
asked to execute it. Supply the same approval reference used for approval. Report the returned
audit entry and no stronger claim.
