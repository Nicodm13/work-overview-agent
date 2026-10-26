package dk.school.workoverviewagent.source.graph;

import dk.school.workoverviewagent.calendar.api.IMeetingAvailabilityProvider;
import dk.school.workoverviewagent.calendar.contract.ScheduleAvailability;
import dk.school.workoverviewagent.calendar.contract.ScheduleCalendar;
import dk.school.workoverviewagent.contact.Contact;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "work-overview.sources", name = "provider", havingValue = "graph", matchIfMissing = true)
class GraphMeetingAvailabilityProvider implements IMeetingAvailabilityProvider {

    private final GraphSourceClient graph;

    GraphMeetingAvailabilityProvider(GraphSourceClient graph) {
        this.graph = graph;
    }

    @Override
    public ScheduleAvailability getAvailability(
        List<Contact> attendees,
        Instant startsAt,
        Instant endsAt,
        int intervalMinutes) {
        var session = graph.openForCurrentUser();
        var profile = session.getJson(graph.uri("/me", Map.of("$select", "id,displayName,mail,userPrincipalName")));
        var organizerAddress = GraphValues.string(profile, "mail");
        if (organizerAddress == null) {
            organizerAddress = GraphValues.string(profile, "userPrincipalName");
        }
        if (organizerAddress == null) {
            throw new IllegalStateException("Microsoft Graph did not return the signed-in user's email address");
        }
        var organizer = new Contact(
            GraphValues.string(profile, "id"),
            GraphValues.string(profile, "displayName") == null ? organizerAddress : GraphValues.string(profile, "displayName"),
            organizerAddress,
            organizerAddress,
            null);

        var scheduleAddresses = new ArrayList<String>();
        scheduleAddresses.add(organizerAddress);
        attendees.stream()
            .map(Contact::emailAddress)
            .filter(address -> address != null && !address.isBlank())
            .filter(address -> scheduleAddresses.stream().noneMatch(address::equalsIgnoreCase))
            .forEach(scheduleAddresses::add);

        var request = Map.<String, Object>of(
            "schedules", scheduleAddresses,
            "startTime", graphDateTime(startsAt),
            "endTime", graphDateTime(endsAt),
            "availabilityViewInterval", intervalMinutes);
        var response = session.postJson(graph.uri("/me/calendar/getSchedule", Map.of()), request);
        var returnedSchedules = GraphValues.objects(response, "value");
        var schedulesByAddress = new LinkedHashMap<String, ScheduleCalendar>();
        for (var schedule : returnedSchedules) {
            var scheduleId = GraphValues.string(schedule, "scheduleId");
            if (scheduleId == null) {
                continue;
            }
            var error = GraphValues.object(schedule, "error");
            var errorCode = GraphValues.string(error, "code");
            var errorMessage = GraphValues.string(error, "message");
            var failure = errorCode == null && errorMessage == null
                ? null
                : "Microsoft Graph could not read this calendar's availability";
            schedulesByAddress.put(scheduleId.toLowerCase(), new ScheduleCalendar(
                scheduleId,
                GraphValues.string(schedule, "availabilityView"),
                failure));
        }

        var calendars = scheduleAddresses.stream()
            .map(address -> schedulesByAddress.getOrDefault(address.toLowerCase(),
                new ScheduleCalendar(address, null, "Microsoft Graph did not return availability for this calendar")))
            .toList();
        return new ScheduleAvailability(organizer, calendars);
    }

    private Map<String, String> graphDateTime(Instant instant) {
        return Map.of(
            "dateTime", LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toString(),
            "timeZone", "UTC");
    }
}
