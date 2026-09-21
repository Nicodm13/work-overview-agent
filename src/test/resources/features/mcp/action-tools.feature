Feature: Action MCP tools

  Scenario: Action tools require approval before execution through MCP
    When the action tool scenario creates a follow-up item with title "Action tool test item"
    And the draft_follow_up_action MCP tool creates a Teams message draft
    Then the action draft references the created follow-up item
    When the approve_action_draft MCP tool records final approval
    Then the action draft is approved
    When the execute_approved_action MCP tool executes the approved draft
    Then the action execution is audited as approved
