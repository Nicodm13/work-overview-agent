Feature: Work status service

  Scenario: New follow-up item starts as unverified digital evidence
    Given no user-confirmed status exists for follow-up item "status-cucumber-1"
    Then the work status is "UNVERIFIED" and the status source is "DIGITAL_EVIDENCE"

  Scenario: User confirmation becomes the current work status
    When the user marks follow-up item "status-cucumber-2" as "RESOLVED" with reason "Clarified verbally."
    Then the work status is "RESOLVED" and the status source is "USER_CONFIRMED"
