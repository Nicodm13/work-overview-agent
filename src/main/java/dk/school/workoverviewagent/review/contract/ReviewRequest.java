package dk.school.workoverviewagent.review.contract;

public record ReviewRequest(
        String userId,
        ReviewScope scope) {
}
