package dk.school.workoverviewagent.contact;

public record Contact(
    String id,
    String displayName,
    String emailAddress,
    String userPrincipalName,
    String jobTitle) {
}
