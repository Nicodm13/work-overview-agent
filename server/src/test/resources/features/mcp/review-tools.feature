Feature: Review MCP tools

  Scenario: Get review exposes the review service through MCP
    When the get_review MCP tool is called for the selected mock evidence
    Then the MCP response contains 1 review item
    And the MCP response uses daily overview purpose

  Scenario: Get review accepts a selected source type and review purpose
    When the get_review MCP tool is called for source type "TEAMS" with purpose "WEEKLY_OVERVIEW"
    Then the MCP response contains 1 review item
    And the MCP response uses source type "TEAMS" and purpose "WEEKLY_OVERVIEW"
