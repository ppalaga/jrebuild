package org.l2x6.jrebuild.core.build.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.build.BuildReport;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.SPLIT_LINES;

public interface BuildReportStorage {
    void store(BuildReport buildReport);

    Stream<BuildReport> list(BuildGroup buildGroup);

    static BuildReportStorage local(BuildMetadataLayout buildMetadataLayout) {
        return new FilesystemBuildReportStorage(buildMetadataLayout);
    }

    static record FilesystemBuildReportStorage(BuildMetadataLayout buildMetadataLayout) implements BuildReportStorage {

        private static final ObjectMapper MAPPER = JsonMapper.builder(new YAMLFactory()
                .disable(SPLIT_LINES)
                .disable(MINIMIZE_QUOTES)
                .enable(INDENT_ARRAYS_WITH_INDICATOR))
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .build().setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);

        @Override
        public void store(BuildReport buildReport) {
            Path dir = buildMetadataLayout.getOrCreateBuildDirectory(buildReport.buildRequest().buildGroup())
                    .resolve(BuildMetadataLayout.format(buildReport.timeStamp()));
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not create " + dir, e);
            }
            Path path = dir.resolve("build-report.yaml");
            try {
                MAPPER.writeValue(path.toFile(), buildReport);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not write build report to " + path, e);
            }
        }

        @Override
        public Stream<BuildReport> list(BuildGroup buildGroup) {
            Path reportsDir = buildMetadataLayout.getOrCreateBuildDirectory(buildGroup);
            try (Stream<Path> files = Files.list(reportsDir)) {
                return files
                        .filter(Files::isDirectory)
                        .map(dir -> dir.resolve("build-report.yaml"))
                        .filter(Files::isRegularFile)
                        .map(file -> {
                            try {
                                return MAPPER.readValue(file.toFile(), BuildReport.class);
                            } catch (IOException e) {
                                throw new UncheckedIOException("Could not read from " + file, e);
                            }
                        });
            } catch (IOException e) {
                throw new UncheckedIOException("Could not list " + reportsDir, e);
            }
        }

    }
}
