package dk.school.workoverviewagent.source.api;

import dk.school.workoverviewagent.source.contract.SourceData;
import dk.school.workoverviewagent.source.contract.SourceRequest;

public interface ISourceAdapterLayer {

    SourceData loadSources(SourceRequest request);
}
