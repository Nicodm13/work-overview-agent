package dk.school.workoverviewagent.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GraphConsentErrorClassifierTest {

    @Test
    void identifiesKnownEntraConsentAndPermissionConfigurationErrors() {
        assertThat(GraphConsentErrorClassifier.requiresConsent("AADSTS65001: consent is required")).isTrue();
        assertThat(GraphConsentErrorClassifier.requiresConsent("AADSTS65004: the user declined consent")).isTrue();
        assertThat(GraphConsentErrorClassifier.requiresConsent("AADSTS65005: application is misconfigured")).isTrue();
        assertThat(GraphConsentErrorClassifier.requiresConsent("AADSTS50011: reply URL mismatch")).isFalse();
    }
}
