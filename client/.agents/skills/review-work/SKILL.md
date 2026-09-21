---
name: review-work
description: Review a selected interval across work sources to identify matters that may need attention.
---

# Review work

Use `get_review` with ISO-8601 start and end instants, relevant source types, and the matching
review purpose. Review all sources unless the user scopes them. Rank the returned items first and
retrieve detailed evidence only for items selected for presentation, tracking, or a suggested
action. Every presented item must receive a concrete suggested next action in the client; the server
does not determine priority, recipient, or completion.
