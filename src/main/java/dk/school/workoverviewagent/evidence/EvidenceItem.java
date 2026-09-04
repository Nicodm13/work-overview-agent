package dk.school.workoverviewagent.evidence;

public record EvidenceItem(
        String id,
        String title,
        String summary,
        String priority,
        String evidenceStatus) {
}
