package dk.school.workoverviewagent.graph;

import java.util.List;

final class GraphConsentErrorClassifier {

    private static final List<String> CONSENT_ERROR_CODES = List.of("AADSTS65001", "AADSTS65004", "AADSTS65005");

    private GraphConsentErrorClassifier() {
    }

    static boolean requiresConsent(String message) {
        return message != null && CONSENT_ERROR_CODES.stream().anyMatch(message::contains);
    }
}
