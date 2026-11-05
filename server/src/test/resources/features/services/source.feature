Feature: Source adapter layer

  Scenario: Load all source types for a selected interval
    Given the prototype source interval covers the selected mock evidence
    Then the loaded source ids are "open-teams-15"
