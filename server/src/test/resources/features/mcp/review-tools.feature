Feature: Review MCP tools

  Scenario: Get review exposes the review service through MCP
    When the get_review MCP tool is called for the selected mock evidence
    Then the MCP response contains 1 review item
    And the MCP response uses all sources

  Scenario: Get review accepts a selected source type
    When the get_review MCP tool is called for source type "TEAMS"
    Then the MCP response contains 1 review item
    And the MCP response uses source type "TEAMS"
