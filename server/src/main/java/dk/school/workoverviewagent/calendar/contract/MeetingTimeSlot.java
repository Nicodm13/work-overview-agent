package dk.school.workoverviewagent.calendar.contract;

import java.time.Instant;

public record MeetingTimeSlot(
    Instant startsAt,
    Instant endsAt,
    String startsAtLocal,
    String endsAtLocal) {
}
