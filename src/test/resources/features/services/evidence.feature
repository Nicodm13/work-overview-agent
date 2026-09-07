Feature: Evidence service

  Scenario: Capture source evidence without interpreting its meaning
    Given a normalized Teams source item with id "teams-cucumber-1"
    Then the evidence is captured with source type "TEAMS" and status "SOURCE_EVIDENCE_CAPTURED"
