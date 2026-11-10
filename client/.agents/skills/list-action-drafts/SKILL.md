---
name: list-action-drafts
description: List active Work Overview action drafts without executing them.
---

# List action drafts

Use `list_action_drafts` before concluding a review or action workflow, including when waiting for
information, unless the user explicitly asks not to be reminded. Mention every unfinished action
with its draft ID, identifying details, and returned state. Distinguish actions created in the current
interaction from older outstanding actions using the creation time and conversation. Do not assume
an older action is obsolete; identify outdated or superseded drafts when known and ask the user what
to do. Clearly distinguish unapproved, approved, executing, and failed actions. A failed action can
have an unknown delivery outcome: use `get_action_audit_log` and do not automatically retry it or claim it
was not sent. Do not delete an outdated draft without explicit user instruction. A list or summary
never approves or executes any action.
