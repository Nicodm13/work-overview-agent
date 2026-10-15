Feature: Action MCP tools

  Scenario: Action tools require approval before execution through MCP
    When the action tool scenario creates a follow-up item with title "Action tool test item"
    And the draft_follow_up_action MCP tool creates a Teams message draft
    Then the action draft references the created follow-up item
    When the execute_approved_action MCP tool attempts to execute the unapproved draft
    Then the unapproved action cannot be executed
    When the approve_action_draft MCP tool records final approval
    Then the action draft is approved
    When the execute_approved_action MCP tool executes the approved draft
    Then the action execution is audited as approved

  Scenario: Meeting invitation drafts remain editable until approval
    When the action tool scenario creates a follow-up item with title "Clarify the integration approach"
    And the draft_follow_up_action MCP tool creates a meeting invitation draft
    Then the meeting invitation draft contains the proposed details
    When the update_action_draft MCP tool edits the meeting invitation draft
    Then the meeting invitation draft contains the edited details
    When the approve_action_draft MCP tool records final approval
    And the update_action_draft MCP tool attempts to edit the approved draft
    Then the approved action draft cannot be edited
