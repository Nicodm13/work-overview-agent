Feature: Status MCP tools

  Scenario: Status tools expose user-confirmed status through MCP
    When the status tool scenario creates a follow-up item with title "Status tool test item"
    And the update_status MCP tool marks the created follow-up item as "RESOLVED" with reason "Clarified verbally."
    Then the status update is "RESOLVED" from "USER_CONFIRMED"
    When the get_status MCP tool is called for the created follow-up item
    Then the retrieved status is "RESOLVED" from "USER_CONFIRMED"
