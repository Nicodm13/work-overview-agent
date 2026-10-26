package dk.school.workoverviewagent.contact.api;

import dk.school.workoverviewagent.contact.Contact;

import java.util.List;

public interface IContactDirectory {

    List<Contact> search(String query);
}
