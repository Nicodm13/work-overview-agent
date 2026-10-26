package dk.school.workoverviewagent.calendar.api;

import dk.school.workoverviewagent.contact.Contact;
import dk.school.workoverviewagent.calendar.contract.ScheduleAvailability;

import java.time.Instant;
import java.util.List;

public interface IMeetingAvailabilityProvider {

    ScheduleAvailability getAvailability(List<Contact> attendees, Instant startsAt, Instant endsAt, int intervalMinutes);
}
