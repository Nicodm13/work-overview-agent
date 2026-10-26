package dk.school.workoverviewagent.calendar.contract;

import java.time.Instant;
import java.util.List;

public record MeetingAvailabilityResponse(
    MeetingParticipant organizer,
    List<MeetingParticipant> attendees,
    Instant searchStartsAt,
    Instant searchEndsAt,
    int meetingDurationMinutes,
    String timeZone,
    List<MeetingTimeSlot> suggestions,
    List<String> limitations) {

    public MeetingAvailabilityResponse {
        attendees = attendees == null ? List.of() : List.copyOf(attendees);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
