package dk.school.workoverviewagent.source.adapter;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.api.ISourceAdapter;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.source.contract.SourceResult;
import dk.school.workoverviewagent.source.contract.SourceCoverage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceAdapterLayerTest {

    @Test
    void keepsSuccessfulResultsWhenOneSourceFails() {
        var layer = configuredLayer(
            failing(SourceType.TEAMS), successful(SourceType.OUTLOOK, "mail-1"));

        var data = layer.loadSources(request(SourceType.TEAMS, SourceType.OUTLOOK));

        assertThat(data.items()).extracting(SourceItem::id).containsExactly("mail-1");
        assertThat(data.limitations()).containsExactly(
            "TEAMS source could not be loaded; its results are unavailable.");
        assertThat(data.limitations()).noneMatch(value -> value.contains("secret Graph response"));
        assertThat(data.coverage()).isEqualTo(SourceCoverage.PARTIAL);
    }

    @Test
    void reportsMultipleFailuresWithoutDiscardingSuccessfulResults() {
        var layer = configuredLayer(
            failing(SourceType.TEAMS), successful(SourceType.OUTLOOK, "mail-2"),
            failing(SourceType.CALENDAR));

        var data = layer.loadSources(request(
            SourceType.TEAMS, SourceType.OUTLOOK, SourceType.CALENDAR));

        assertThat(data.items()).extracting(SourceItem::id).containsExactly("mail-2");
        assertThat(data.limitations()).containsExactly(
            "TEAMS source could not be loaded; its results are unavailable.",
            "CALENDAR source could not be loaded; its results are unavailable.");
        assertThat(data.coverage()).isEqualTo(SourceCoverage.PARTIAL);
    }

    @Test
    void reportsAllSourcesFailedInsteadOfAValidEmptyReview() {
        var layer = configuredLayer(failing(SourceType.TEAMS), failing(SourceType.CALENDAR));

        var data = layer.loadSources(request(SourceType.TEAMS, SourceType.CALENDAR));

        assertThat(data.items()).isEmpty();
        assertThat(data.limitations()).containsExactly(
            "TEAMS source could not be loaded; its results are unavailable.",
            "CALENDAR source could not be loaded; its results are unavailable.",
            "All selected sources failed; review is unavailable.");
        assertThat(data.coverage()).isEqualTo(SourceCoverage.FAILED);
    }

    @Test
    void validEmptySourceHasNoFailureLimitation() {
        var layer = configuredLayer(successful(SourceType.OUTLOOK, null));

        var data = layer.loadSources(request(SourceType.OUTLOOK));

        assertThat(data.items()).isEmpty();
        assertThat(data.limitations()).isEmpty();
        assertThat(data.coverage()).isEqualTo(SourceCoverage.COMPLETE);
    }

    @Test
    void rejectsMissingAdaptersAtStartup() {
        assertThatThrownBy(() -> new SourceAdapterLayer(List.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Expected exactly one source adapter");
    }

    @Test
    void explainsMissingGraphConsentWithoutExposingTheAuthenticationError() {
        var layer = configuredLayer(failing(SourceType.OUTLOOK,
            new IllegalStateException("Token acquisition failed",
                new IllegalStateException("AADSTS65001: confidential tenant details"))));

        var data = layer.loadSources(request(SourceType.OUTLOOK));

        assertThat(data.limitations()).containsExactly(
            "OUTLOOK source requires Microsoft Graph consent. Ask a tenant administrator to review and grant the app's delegated Graph permissions.",
            "All selected sources failed; review is unavailable.");
        assertThat(data.limitations()).noneMatch(value -> value.contains("confidential"));
        assertThat(data.coverage()).isEqualTo(SourceCoverage.FAILED);
    }

    @Test
    void explainsGraphAccessDenialWithoutClaimingConsentIsDefinitelyMissing() {
        var layer = configuredLayer(failing(SourceType.CALENDAR,
            new HttpClientErrorException(HttpStatus.FORBIDDEN, "confidential Graph response")));

        var data = layer.loadSources(request(SourceType.CALENDAR));

        assertThat(data.limitations()).containsExactly(
            "CALENDAR source was denied by Microsoft Graph. Check Calendars.Read delegated permission and consent, and the signed-in account's calendar access.",
            "All selected sources failed; review is unavailable.");
        assertThat(data.limitations()).noneMatch(value -> value.contains("confidential"));
    }

    private SourceRequest request(SourceType... sources) {
        return new SourceRequest("owner-1", Instant.parse("2026-09-01T00:00:00Z"),
            Instant.parse("2026-09-02T00:00:00Z"), List.of(sources));
    }

    private SourceAdapterLayer configuredLayer(ISourceAdapter... selectedAdapters) {
        var adapters = new ArrayList<>(List.of(selectedAdapters));
        for (var sourceType : SourceType.values()) {
            if (adapters.stream().noneMatch(adapter -> adapter.sourceType() == sourceType)) {
                adapters.add(successful(sourceType, null));
            }
        }
        return new SourceAdapterLayer(adapters);
    }

    private ISourceAdapter failing(SourceType sourceType) {
        return failing(sourceType, new IllegalStateException("secret Graph response"));
    }

    private ISourceAdapter failing(SourceType sourceType, RuntimeException failure) {
        return new ISourceAdapter() {
            @Override
            public SourceType sourceType() {
                return sourceType;
            }

            @Override
            public SourceResult load(SourceRequest request) {
                throw failure;
            }
        };
    }

    private ISourceAdapter successful(SourceType sourceType, String itemId) {
        return new ISourceAdapter() {
            @Override
            public SourceType sourceType() {
                return sourceType;
            }

            @Override
            public SourceResult load(SourceRequest request) {
                if (itemId == null) {
                    return new SourceResult(sourceType, List.of(), List.of());
                }
                return new SourceResult(sourceType, List.of(new SourceItem(
                    itemId, sourceType, Instant.parse("2026-09-01T12:00:00Z"),
                    "Subject", "Content", "Sender", List.of(), Map.of())), List.of());
            }
        };
    }
}
