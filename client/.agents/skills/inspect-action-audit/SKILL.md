---
name: inspect-action-audit
description: Read the signed-in user's action execution audit entries and delivery outcomes.
---

# Inspect action audit

Use `get_action_audit_log` to inspect recorded execution outcomes, particularly for failed actions.
Match entries to actions using their recorded references and context; do not assume a match when it
is unclear. Report unknown outcomes as unknown and do not automatically retry. Audit entries are
read-only and cannot be edited through this tool.
