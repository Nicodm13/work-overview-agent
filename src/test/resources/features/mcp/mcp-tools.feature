Feature: Work overview MCP tools

  Scenario: Get review exposes the review service through MCP
    When the get_review MCP tool is called from "2026-09-04T00:00:00Z" to "2026-09-04T23:59:59Z"
    Then the MCP response contains 5 review items
    And the MCP response uses daily overview purpose
