Feature: Action service

  Scenario: Outbound action requires explicit final approval
    Given a Teams action draft for evidence "action-cucumber-1"
    When the action is executed without final approval
    Then the action execution is rejected

  Scenario: Approved action is executed and audited
    Given a Teams action draft for evidence "action-cucumber-2"
    When the action is approved and executed
    Then the action is audited as approved for evidence "action-cucumber-2"
