package dk.school.workoverviewagent.mcp;

import dk.school.workoverviewagent.calendar.MeetingAvailabilityService;
import dk.school.workoverviewagent.calendar.contract.MeetingAvailabilityResponse;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class MeetingAvailabilityTools {

    private final MeetingAvailabilityService meetingAvailabilityService;
    private final IUserProvider userProvider;

    public MeetingAvailabilityTools(
        MeetingAvailabilityService meetingAvailabilityService,
        IUserProvider userProvider) {
        this.meetingAvailabilityService = meetingAvailabilityService;
        this.userProvider = userProvider;
    }

    @McpTool(
        name = "find_meeting_times",
        description = "Use proactively when an actionable finding needs a joint decision or clarification from several evidence-supported people; do not ask the user to provide a time when calendar lookup can find one. Resolve named participants from the evidence with find_contacts, excluding anyone not supported by the source. Check the signed-in user's and attendees' free/busy data and return up to five shared free slots; availability details are not returned. Prefer a 30-minute slot within the next two weeks, or before an evidence-based deadline. This tool never creates or sends an invitation. After choosing a suitable slot, create an editable MEETING_INVITATION draft with title, selected time, all supported invitees, and an agenda based on the open issue, then show those details for edits and explicit approval.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true))
    public MeetingAvailabilityResponse findMeetingTimes(
        @McpToolParam(description = "Attendee display names or tenant email addresses, excluding the signed-in organizer.", required = true)
        List<String> attendees,
        @McpToolParam(description = "Earliest candidate time as an ISO-8601 instant.", required = true)
        String startsAt,
        @McpToolParam(description = "Latest candidate time as an ISO-8601 instant.", required = true)
        String endsAt,
        @McpToolParam(description = "Meeting duration in minutes, in 15-minute increments.", required = true)
        int durationMinutes,
        @McpToolParam(description = "IANA time zone for displaying suggestions, for example Europe/Copenhagen. Defaults to Europe/Copenhagen.", required = false)
        String timeZone) {
        return meetingAvailabilityService.findMeetingTimes(
            userProvider.getUserId(),
            attendees,
            Instant.parse(startsAt),
            Instant.parse(endsAt),
            durationMinutes,
            timeZone);
    }
}
