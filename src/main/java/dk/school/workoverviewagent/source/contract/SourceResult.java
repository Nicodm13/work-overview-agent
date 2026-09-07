package dk.school.workoverviewagent.source.contract;

import dk.school.workoverviewagent.model.SourceType;
import java.util.List;

public record SourceResult(
        SourceType sourceType,
        List<SourceItem> items,
        List<String> limitations) {

    public SourceResult {
        items = items == null ? List.of() : List.copyOf(items);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
