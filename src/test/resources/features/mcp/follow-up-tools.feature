Feature: Follow-up MCP tools

  Scenario: Follow-up tools expose explicit follow-up tracking through MCP
    When the create_follow_up_item MCP tool is called with title "Test environment clarification"
    Then the created follow-up item has no evidence references
    When the get_follow_up_item MCP tool is called for the created follow-up item
    Then the retrieved follow-up item matches the created follow-up item
    When the attach_evidence_to_follow_up MCP tool is called with Teams evidence
    Then the follow-up item has 1 explicitly linked evidence reference
    When the list_follow_up_items MCP tool is called
    Then the list contains the created follow-up item
