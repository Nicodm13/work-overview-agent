Feature: Action service

  Scenario: Draft approval requires explicit final approval
    Given a Teams action draft for follow-up item "action-cucumber-1"
    When the action is approved without final approval
    Then the action approval is rejected

  Scenario: Approved action is executed and audited
    Given a Teams action draft for follow-up item "action-cucumber-2"
    When the action is approved and executed
    Then the action is audited as approved for follow-up item "action-cucumber-2"
