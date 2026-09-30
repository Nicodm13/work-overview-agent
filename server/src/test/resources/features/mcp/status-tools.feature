Feature: Status MCP tools

  Scenario: Status tools expose user-confirmed status through MCP
    When the status tool scenario creates a follow-up item with title "Status tool test item"
    And the update_status MCP tool marks the created follow-up item as "RESOLVED" with reason "Clarified verbally."
    Then the status update is "RESOLVED" from "USER_CONFIRMED"
    When the get_status MCP tool is called for the created follow-up item
    Then the retrieved status is "RESOLVED" from "USER_CONFIRMED"

  Scenario: New linked evidence does not change a resolved status
    When the status tool scenario creates a follow-up item with title "Resolved item with new evidence"
    And the update_status MCP tool marks the created follow-up item as "RESOLVED" with reason "The user confirmed it was done."
    And later email evidence is linked to the created follow-up item
    When the find_new_evidence MCP tool is called
    Then the new evidence result contains the created follow-up item
    When the get_status MCP tool is called for the created follow-up item
    Then the retrieved status is "RESOLVED" from "USER_CONFIRMED"
    When the update_status MCP tool marks the created follow-up item as "OPEN" with reason "The user decided to reopen it."
    And the find_new_evidence MCP tool is called
    Then the new evidence result is empty
