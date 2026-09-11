Feature: Follow-up service

  Scenario: Explicitly linked evidence can span source channels
    Given a follow-up item with Teams evidence
    When email evidence is attached to the follow-up item
    Then the follow-up item contains Teams and OUTLOOK evidence
