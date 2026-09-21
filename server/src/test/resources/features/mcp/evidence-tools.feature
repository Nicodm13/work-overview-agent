Feature: Evidence MCP tools

  Scenario: Get item evidence exposes the evidence service through MCP
    When the evidence tool scenario requests a review from "2026-09-04T00:00:00Z" to "2026-09-04T23:59:59Z"
    And the get_item_evidence MCP tool is called for the first review item
    Then the evidence MCP response contains 1 source reference
    And the evidence MCP response has source type "TEAMS"
