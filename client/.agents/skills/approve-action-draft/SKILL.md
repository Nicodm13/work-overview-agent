---
name: approve-action-draft
description: Record final approval for the exact displayed Work Overview action draft.
---

# Approve action draft

Use `approve_action_draft` only after explicit final user approval of the exact displayed draft.
Supply the draft ID and final approval. The tool returns an approval reference bound to the exact
draft content; use that returned reference when executing. Any content or recipient change
invalidates the approval boundary.
