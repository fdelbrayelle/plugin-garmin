package io.kestra.plugin.garmin;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchOutput;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractListTask extends AbstractTimeRangeTask implements RunnableTask<FetchOutput> {
    @Schema(
        title = "How to return the records",
        description = """
            `FETCH` returns all records in the `rows` output, `FETCH_ONE` only the first one in `row`,
            `STORE` writes them to Kestra internal storage and returns the `uri`, and `NONE` returns only the `size`.
            Use `STORE` for large result sets."""
    )
    @Builder.Default
    @PluginProperty(group = "destination")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    protected abstract String path();

    @Override
    public FetchOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rFetchType = runContext.render(fetchType).as(FetchType.class).orElse(FetchType.FETCH);
        var query = timeRangeQuery(runContext);

        List<Map<String, Object>> records;
        try (var client = GarminClient.create(runContext, this)) {
            records = client.getList(path(), query);
        }
        logger.info("Fetched {} record(s) from Garmin '{}'", records.size(), path());

        var output = FetchOutput.builder().size((long) records.size());
        switch (rFetchType) {
            case FETCH -> output.rows(new ArrayList<>(records));
            case FETCH_ONE -> output.row(records.isEmpty() ? null : records.getFirst());
            case STORE -> output.uri(store(runContext, records));
            case NONE -> {
            }
        }
        return output.build();
    }

    private static URI store(RunContext runContext, List<Map<String, Object>> records) throws IOException {
        var tempFile = runContext.workingDir().createTempFile(".ion").toFile();
        try (var out = new BufferedOutputStream(Files.newOutputStream(tempFile.toPath()))) {
            for (var record : records) {
                FileSerde.write(out, record);
            }
        }
        return runContext.storage().putFile(tempFile);
    }
}
