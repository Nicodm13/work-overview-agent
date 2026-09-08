package dk.school.workoverviewagent.services;

import static org.assertj.core.api.Assertions.assertThat;
import dk.school.workoverviewagent.source.api.ISourceAdapterLayer;
import dk.school.workoverviewagent.source.contract.SourceData;
import dk.school.workoverviewagent.source.contract.SourceItem;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class SourceStepDefinitions {
    @Autowired private ISourceAdapterLayer sourceAdapterLayer;
    private SourceData sourceData;

    @Given("the prototype source interval is from {string} to {string}")
    public void sourceInterval(String startsAt, String endsAt) {
        sourceData = sourceAdapterLayer.loadSources(new SourceRequest(Instant.parse(startsAt), Instant.parse(endsAt), List.of()));
    }

    @Then("the loaded source ids are {string}")
    public void loadedSourceIds(String ids) {
        assertThat(sourceData.items()).extracting(SourceItem::id).containsExactlyElementsOf(List.of(ids.split(",")));
    }
}
