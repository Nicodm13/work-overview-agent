package dk.school.workoverviewagent.services;

import dk.school.workoverviewagent.source.api.ISourceAdapterLayer;
import dk.school.workoverviewagent.source.contract.SourceData;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class SourceStepDefinitions {
    @Autowired
    private ISourceAdapterLayer sourceAdapterLayer;
    @Autowired
    private Instant mockEvidenceTimestamp;
    private SourceData sourceData;

    @Given("the prototype source interval covers the selected mock evidence")
    public void sourceInterval() {
        sourceData = sourceAdapterLayer.loadSources(new SourceRequest(
            "user-1",
            mockEvidenceTimestamp,
            mockEvidenceTimestamp,
            List.of()));
    }

    @Then("the loaded source ids are {string}")
    public void loadedSourceIds(String ids) {
        assertThat(sourceData.items()).extracting(SourceItem::id).containsExactlyElementsOf(List.of(ids.split(",")));
    }
}
