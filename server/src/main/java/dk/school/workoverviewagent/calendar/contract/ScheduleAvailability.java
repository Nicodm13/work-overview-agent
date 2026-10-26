package dk.school.workoverviewagent.calendar.contract;

import dk.school.workoverviewagent.contact.Contact;

import java.util.List;

public record ScheduleAvailability(Contact organizer, List<ScheduleCalendar> calendars) {

    public ScheduleAvailability {
        calendars = calendars == null ? List.of() : List.copyOf(calendars);
    }
}
