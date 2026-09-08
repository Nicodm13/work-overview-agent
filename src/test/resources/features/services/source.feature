Feature: Source adapter layer

  Scenario: Load all source types for a selected interval
    Given the prototype source interval is from "2026-09-04T00:00:00Z" to "2026-09-04T23:59:59Z"
    Then the loaded source ids are "teams-2026-09-04-001,email-2026-09-04-001,calendar-2026-09-04-001,meeting-notes-2026-09-04-001,teams-2026-09-04-002"
