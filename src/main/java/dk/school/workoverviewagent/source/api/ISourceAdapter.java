package dk.school.workoverviewagent.source.api;

import dk.school.workoverviewagent.model.SourceType;
import dk.school.workoverviewagent.source.contract.SourceRequest;
import dk.school.workoverviewagent.source.contract.SourceResult;

public interface ISourceAdapter {

    SourceType sourceType();

    SourceResult load(SourceRequest request);
}
