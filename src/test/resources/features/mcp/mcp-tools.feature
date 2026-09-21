Feature: Work overview MCP tools

  Scenario: Get review exposes the review service through MCP
    When the get_review MCP tool is called from "2026-09-04T00:00:00Z" to "2026-09-04T23:59:59Z"
    Then the MCP response contains 5 review items
    And the MCP response uses daily overview purpose

  Scenario: Get item evidence exposes the evidence service through MCP
    When the get_review MCP tool is called from "2026-09-04T00:00:00Z" to "2026-09-04T23:59:59Z"
    And the get_item_evidence MCP tool is called for the first review item
    Then the evidence MCP response contains 1 source reference
    And the evidence MCP response has source type "TEAMS"

  Scenario: Follow-up tools expose explicit follow-up tracking through MCP
    When the create_follow_up_item MCP tool is called with title "Test environment clarification"
    Then the created follow-up item has no evidence references
    When the get_follow_up_item MCP tool is called for the created follow-up item
    Then the retrieved follow-up item matches the created follow-up item
    When the attach_evidence_to_follow_up MCP tool is called with Teams evidence
    Then the follow-up item has 1 explicitly linked evidence reference
    When the list_follow_up_items MCP tool is called
    Then the list contains the created follow-up item
