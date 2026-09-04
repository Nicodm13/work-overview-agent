package dk.school.workoverviewagent.source;

import java.util.List;

public record SourceData(
        List<String> records,
        List<String> limitations) {

    public SourceData {
        records = records == null ? List.of() : List.copyOf(records);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
