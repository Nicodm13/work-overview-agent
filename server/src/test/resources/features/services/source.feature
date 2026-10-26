Feature: Source adapter layer

  Scenario: Load all source types for a selected interval
    Given the prototype source interval is from "2026-09-04T00:00:00Z" to "2026-09-04T23:59:59Z"
    Then the loaded source ids are "event-2026-09-04-sync,mail-lynne-releasepack-02,mail-nidm-lynne-releasepack-02,release-chat-006"
