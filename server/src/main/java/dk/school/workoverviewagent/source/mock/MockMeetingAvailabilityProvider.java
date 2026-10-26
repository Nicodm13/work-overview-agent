package dk.school.workoverviewagent.source.mock;

import dk.school.workoverviewagent.calendar.api.IMeetingAvailabilityProvider;
import dk.school.workoverviewagent.calendar.contract.ScheduleAvailability;
import dk.school.workoverviewagent.calendar.contract.ScheduleCalendar;
import dk.school.workoverviewagent.contact.Contact;
import dk.school.workoverviewagent.contact.api.IContactDirectory;
import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(prefix = "work-overview.sources", name = "provider", havingValue = "mock")
class MockMeetingAvailabilityProvider implements IMeetingAvailabilityProvider {

    private final MockSourceDataLoader dataLoader;
    private final IContactDirectory contactDirectory;
    private final IUserProvider userProvider;

    MockMeetingAvailabilityProvider(
        MockSourceDataLoader dataLoader,
        IContactDirectory contactDirectory,
        IUserProvider userProvider) {
        this.dataLoader = dataLoader;
        this.contactDirectory = contactDirectory;
        this.userProvider = userProvider;
    }

    @Override
    public ScheduleAvailability getAvailability(
        List<Contact> attendees,
        Instant startsAt,
        Instant endsAt,
        int intervalMinutes) {
        var currentUserPrincipalName = userProvider.getUserPrincipalName();
        var organizer = contactDirectory.search(currentUserPrincipalName).stream()
            .filter(contact -> equalsIgnoreCase(contact.emailAddress(), currentUserPrincipalName)
                || equalsIgnoreCase(contact.userPrincipalName(), currentUserPrincipalName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "The signed-in user is not present in the mock-data users.csv file"));
        var participants = Stream.concat(Stream.of(organizer), attendees.stream()).toList();
        var events = dataLoader.loadItems("calendar.csv", SourceType.CALENDAR);
        var slotCount = Math.toIntExact(Duration.between(startsAt, endsAt).toMinutes() / intervalMinutes);
        var calendars = participants.stream()
            .map(participant -> new ScheduleCalendar(
                participant.emailAddress(),
                availabilityView(participant, events, startsAt, intervalMinutes, slotCount),
                null))
            .toList();
        return new ScheduleAvailability(organizer, calendars);
    }

    private String availabilityView(
        Contact participant,
        List<SourceItem> events,
        Instant startsAt,
        int intervalMinutes,
        int slotCount) {
        var view = new StringBuilder(slotCount);
        for (var slot = 0; slot < slotCount; slot++) {
            var slotStart = startsAt.plus(Duration.ofMinutes((long) slot * intervalMinutes));
            var slotEnd = slotStart.plus(Duration.ofMinutes(intervalMinutes));
            var busy = events.stream().anyMatch(event -> includes(event, participant)
                && overlaps(event, slotStart, slotEnd));
            view.append(busy ? '2' : '0');
        }
        return view.toString();
    }

    private boolean includes(SourceItem event, Contact participant) {
        return matches(participant, event.senderOrOrganizer())
            || event.participants().stream().anyMatch(name -> matches(participant, name));
    }

    private boolean matches(Contact participant, String identity) {
        return equalsIgnoreCase(participant.displayName(), identity)
            || equalsIgnoreCase(participant.emailAddress(), identity)
            || equalsIgnoreCase(participant.userPrincipalName(), identity);
    }

    private boolean overlaps(SourceItem event, Instant slotStart, Instant slotEnd) {
        var startsAt = instant(event.attributes().get("startsAt"), event.occurredAt());
        var endsAt = instant(event.attributes().get("endsAt"), startsAt.plus(Duration.ofMinutes(30)));
        return startsAt.isBefore(slotEnd) && endsAt.isAfter(slotStart);
    }

    private Instant instant(String value, Instant fallback) {
        return value == null || value.isBlank() ? fallback : Instant.parse(value);
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.strip().equalsIgnoreCase(right.strip());
    }
}
