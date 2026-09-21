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
