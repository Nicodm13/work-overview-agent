package dk.school.workoverviewagent.source.contract;

import java.util.List;

public record SourceData(
    List<SourceItem> items,
    List<String> limitations,
    SourceCoverage coverage) {

    public SourceData {
        items = items == null ? List.of() : List.copyOf(items);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        if (coverage == null) {
            throw new IllegalArgumentException("coverage must not be null");
        }
    }
}
