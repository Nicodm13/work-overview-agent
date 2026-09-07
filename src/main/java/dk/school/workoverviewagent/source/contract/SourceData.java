package dk.school.workoverviewagent.source.contract;

import java.util.List;

public record SourceData(
        List<SourceItem> items,
        List<String> limitations) {

    public SourceData {
        items = items == null ? List.of() : List.copyOf(items);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
