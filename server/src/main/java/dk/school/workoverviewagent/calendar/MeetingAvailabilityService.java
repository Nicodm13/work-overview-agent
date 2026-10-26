package dk.school.workoverviewagent.calendar;

import dk.school.workoverviewagent.calendar.api.IMeetingAvailabilityProvider;
import dk.school.workoverviewagent.calendar.contract.MeetingAvailabilityResponse;
import dk.school.workoverviewagent.calendar.contract.MeetingParticipant;
import dk.school.workoverviewagent.calendar.contract.MeetingTimeSlot;
import dk.school.workoverviewagent.contact.Contact;
import dk.school.workoverviewagent.contact.api.IContactDirectory;
import dk.school.workoverviewagent.user.IUserProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
public class MeetingAvailabilityService {

    private static final int INTERVAL_MINUTES = 15;
    private static final int MAX_ATTENDEES = 19;
    private static final int MAX_SUGGESTIONS = 5;
    private static final Duration MAX_SEARCH_WINDOW = Duration.ofDays(62);
    private static final DateTimeFormatter LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    private final IMeetingAvailabilityProvider availabilityProvider;
    private final IContactDirectory contactDirectory;
    private final IUserProvider userProvider;

    public MeetingAvailabilityService(
        IMeetingAvailabilityProvider availabilityProvider,
        IContactDirectory contactDirectory,
        IUserProvider userProvider) {
        this.availabilityProvider = availabilityProvider;
        this.contactDirectory = contactDirectory;
        this.userProvider = userProvider;
    }

    public MeetingAvailabilityResponse findMeetingTimes(
        String ownerId,
        List<String> attendeeQueries,
        Instant searchStartsAt,
        Instant searchEndsAt,
        int meetingDurationMinutes,
        String requestedTimeZone) {
        if (!userProvider.getUserId().equals(ownerId)) {
            throw new SecurityException("Meeting availability can only be checked for the authenticated user");
        }
        if (attendeeQueries == null || attendeeQueries.isEmpty()) {
            throw new IllegalArgumentException("At least one meeting attendee is required");
        }
        if (attendeeQueries.size() > MAX_ATTENDEES) {
            throw new IllegalArgumentException("A meeting can have at most 19 invited attendees");
        }
        if (searchStartsAt == null || searchEndsAt == null || !searchEndsAt.isAfter(searchStartsAt)) {
            throw new IllegalArgumentException("The meeting search end must be after its start");
        }
        if (Duration.between(searchStartsAt, searchEndsAt).compareTo(MAX_SEARCH_WINDOW) > 0) {
            throw new IllegalArgumentException("The meeting search window cannot exceed 62 days");
        }
        if (meetingDurationMinutes < INTERVAL_MINUTES || meetingDurationMinutes > 480
            || meetingDurationMinutes % INTERVAL_MINUTES != 0) {
            throw new IllegalArgumentException("Meeting duration must be 15 to 480 minutes in 15-minute increments");
        }
        var timeZoneId = requestedTimeZone == null || requestedTimeZone.isBlank()
            ? ZoneId.of("Europe/Copenhagen")
            : ZoneId.of(requestedTimeZone);

        var attendeesByAddress = new LinkedHashMap<String, Contact>();
        for (var query : attendeeQueries) {
            var contact = resolveContact(query);
            var address = contact.emailAddress();
            if (address == null || address.isBlank()) {
                address = contact.userPrincipalName();
            }
            if (address == null || address.isBlank()) {
                throw new IllegalArgumentException("The selected attendee has no email address: " + query);
            }
            attendeesByAddress.putIfAbsent(address.toLowerCase(Locale.ROOT), new Contact(
                contact.id(), contact.displayName(), address, contact.userPrincipalName(), contact.jobTitle()));
        }
        if (attendeesByAddress.isEmpty()) {
            throw new IllegalArgumentException("At least one attendee other than the organizer is required");
        }

        var normalizedStart = ceilToInterval(searchStartsAt);
        var normalizedEnd = floorToInterval(searchEndsAt);
        if (!normalizedEnd.isAfter(normalizedStart)
            || Duration.between(normalizedStart, normalizedEnd).toMinutes() < meetingDurationMinutes) {
            throw new IllegalArgumentException("The selected search window is shorter than the meeting duration");
        }

        var requestedAttendees = List.copyOf(attendeesByAddress.values());
        var availability = availabilityProvider.getAvailability(
            requestedAttendees, normalizedStart, normalizedEnd, INTERVAL_MINUTES);
        var organizer = availability.organizer();
        var attendees = requestedAttendees.stream()
            .filter(contact -> !contact.emailAddress().equalsIgnoreCase(organizer.emailAddress()))
            .toList();
        if (attendees.isEmpty()) {
            throw new IllegalArgumentException("At least one attendee other than the organizer is required");
        }
        var organizerParticipant = new MeetingParticipant(
            organizer.displayName(), organizer.emailAddress(), true);
        var attendeeParticipants = attendees.stream()
            .map(contact -> new MeetingParticipant(contact.displayName(), contact.emailAddress(), false))
            .toList();

        var limitations = new ArrayList<String>();
        var calendarsByAddress = new LinkedHashMap<String, String>();
        for (var calendar : availability.calendars()) {
            if (calendar.error() != null || calendar.availabilityView() == null) {
                limitations.add("Availability could not be confirmed for " + calendar.emailAddress() + ".");
            } else {
                calendarsByAddress.put(calendar.emailAddress().toLowerCase(Locale.ROOT), calendar.availabilityView());
            }
        }
        var expectedAddresses = new ArrayList<String>();
        expectedAddresses.add(organizer.emailAddress());
        attendees.stream().map(Contact::emailAddress).forEach(expectedAddresses::add);
        for (var address : expectedAddresses) {
            if (!calendarsByAddress.containsKey(address.toLowerCase(Locale.ROOT))
                && limitations.stream().noneMatch(message -> message.contains(address))) {
                limitations.add("Availability was not returned for " + address + ".");
            }
        }

        var suggestions = limitations.isEmpty()
            ? findOpenSlots(expectedAddresses, calendarsByAddress, normalizedStart, normalizedEnd,
                meetingDurationMinutes, timeZoneId)
            : List.<MeetingTimeSlot>of();

        return new MeetingAvailabilityResponse(
            organizerParticipant,
            attendeeParticipants,
            normalizedStart,
            normalizedEnd,
            meetingDurationMinutes,
            timeZoneId.getId(),
            suggestions,
            limitations);
    }

    private List<MeetingTimeSlot> findOpenSlots(
        List<String> expectedAddresses,
        LinkedHashMap<String, String> availabilityViews,
        Instant startsAt,
        Instant endsAt,
        int meetingDurationMinutes,
        ZoneId timeZoneId) {
        var durationSlots = meetingDurationMinutes / INTERVAL_MINUTES;
        var availableSlots = Math.toIntExact(Duration.between(startsAt, endsAt).toMinutes() / INTERVAL_MINUTES);
        var results = new ArrayList<MeetingTimeSlot>();
        for (var startIndex = 0; startIndex + durationSlots <= availableSlots && results.size() < MAX_SUGGESTIONS;
             startIndex++) {
            var allFree = true;
            for (var address : expectedAddresses) {
                var view = availabilityViews.get(address.toLowerCase(Locale.ROOT));
                if (view == null || view.length() < startIndex + durationSlots) {
                    allFree = false;
                    break;
                }
                for (var offset = 0; offset < durationSlots; offset++) {
                    if (view.charAt(startIndex + offset) != '0') {
                        allFree = false;
                        break;
                    }
                }
                if (!allFree) {
                    break;
                }
            }
            if (allFree) {
                var slotStart = startsAt.plus(Duration.ofMinutes((long) startIndex * INTERVAL_MINUTES));
                var slotEnd = slotStart.plus(Duration.ofMinutes(meetingDurationMinutes));
                results.add(new MeetingTimeSlot(
                    slotStart,
                    slotEnd,
                    LOCAL_FORMAT.withZone(timeZoneId).format(slotStart),
                    LOCAL_FORMAT.withZone(timeZoneId).format(slotEnd)));
            }
        }
        return List.copyOf(results);
    }

    private Contact resolveContact(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Attendee names and email addresses must not be blank");
        }
        var normalized = query.strip();
        var matches = contactDirectory.search(normalized).stream()
            .filter(contact -> matches(contact.displayName(), normalized)
                || matches(contact.emailAddress(), normalized)
                || matches(contact.userPrincipalName(), normalized))
            .toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException(matches.isEmpty()
                ? "Could not resolve meeting attendee: " + normalized
                : "Meeting attendee matches multiple tenant users: " + normalized);
        }
        return matches.getFirst();
    }

    private Instant ceilToInterval(Instant instant) {
        var intervalSeconds = INTERVAL_MINUTES * 60L;
        var remainder = Math.floorMod(instant.getEpochSecond(), intervalSeconds);
        return remainder == 0 && instant.getNano() == 0
            ? instant
            : Instant.ofEpochSecond(instant.getEpochSecond() + intervalSeconds - remainder);
    }

    private Instant floorToInterval(Instant instant) {
        var intervalSeconds = INTERVAL_MINUTES * 60L;
        return Instant.ofEpochSecond(instant.getEpochSecond() - Math.floorMod(instant.getEpochSecond(), intervalSeconds));
    }

    private boolean matches(String candidate, String expected) {
        return candidate != null && candidate.strip().equalsIgnoreCase(expected);
    }
}
