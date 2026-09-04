package dk.school.workoverviewagent.source;

import dk.school.workoverviewagent.review.contract.ReviewScope;
import dk.school.workoverviewagent.source.api.ISourceAdapterLayer;
import org.springframework.stereotype.Component;

@Component
class SourceAdapterLayer implements ISourceAdapterLayer {

    @Override
    public SourceData loadSources(String userId, ReviewScope scope) {
        // TODO #6: Load selected source data through Teams, email, calendar, and meeting notes adapters.
        return null;
    }
}
