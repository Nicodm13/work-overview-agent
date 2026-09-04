package dk.school.workoverviewagent.source.api;

import dk.school.workoverviewagent.review.contract.ReviewScope;
import dk.school.workoverviewagent.source.SourceData;

public interface ISourceAdapterLayer {

    SourceData loadSources(String userId, ReviewScope scope);
}
