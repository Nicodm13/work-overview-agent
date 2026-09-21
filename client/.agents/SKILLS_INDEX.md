# Work Overview Client Skills

`../AGENTS.md` defines the Work Overview AI client's overall behavior. The skills below provide
the call boundary and presentation requirements for each MCP tool.

| Skill | MCP tool |
|---|---|
| review-work | `get_review` |
| inspect-evidence | `get_item_evidence` |
| create-follow-up | `create_follow_up_item` |
| retrieve-follow-up | `get_follow_up_item` |
| list-follow-ups | `list_follow_up_items` |
| attach-follow-up-evidence | `attach_evidence_to_follow_up` |
| read-work-status | `get_status` |
| confirm-work-status | `update_status` |
| draft-follow-up-action | `draft_follow_up_action` |
| approve-action-draft | `approve_action_draft` |
| inspect-action-draft | `get_action_draft` |
| list-action-drafts | `list_action_drafts` |
| discard-action-draft | `delete_action_draft` |
| execute-approved-action | `execute_approved_action` |
