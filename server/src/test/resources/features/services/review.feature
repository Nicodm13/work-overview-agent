Feature: Review service

  Scenario: Review returns evidence-based overview items
    When the review service reviews "2026-09-04T00:00:00Z" to "2026-09-04T23:59:59Z"
    Then the review contains the titles "Test environment clarification,Decision needed: integration approach,Project Alpha status review,Project Alpha status review notes,API contract follow-up"
