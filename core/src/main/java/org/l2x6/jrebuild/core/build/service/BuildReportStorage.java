package org.l2x6.jrebuild.core.build.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.file.FileSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.build.BuildReport;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.SPLIT_LINES;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

public interface BuildReportStorage {
    Uni<BuildReport> store(BuildReport buildReport);

    Multi<BuildReport> list(BuildGroup buildGroup);

    /**
     * Layout:
     *
     * <pre>{@code
     * builds
     * + org/group1/project/version/<tag>
     * | + 2026-03-04T11-22-33 // attempt timestamp
     * | | +- build-report.yaml
     * | + 2026-03-05T22-33-44 // attempt timestamp
     * | | +- build-report.yaml
     * }</pre>
     *
     */
    static BuildReportStorage local(FileSystem fileSystem, Path reportsDirectory) {
        return new FilesystemBuildReportStorage(fileSystem, reportsDirectory);
    }

    static record FilesystemBuildReportStorage(FileSystem fileSystem, Path reportsDirectory) implements BuildReportStorage {
        private static final DateTimeFormatter DIR_FORMAT = new DateTimeFormatterBuilder().parseCaseInsensitive()
                .append(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral('T')
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral('-')
                .appendValue(MINUTE_OF_HOUR, 2)
                .optionalStart()
                .appendLiteral('-')
                .appendValue(SECOND_OF_MINUTE, 2)
                .optionalStart()
                .appendFraction(NANO_OF_SECOND, 0, 9, true)
                .toFormatter(Locale.ROOT);

        public static String format(ZonedDateTime ts) {
            return DIR_FORMAT.format(ts);
        }

        @SuppressWarnings("unused")
        Uni<Path> getOrCreateReportsDirectory(BuildGroup buildGroup) {
            FqScmRef scmRef = buildGroup.scmRef();
            Path result = reportsDirectory.resolve(GitUtils.uriToFileName(scmRef.repository().uri()))
                    .resolve(scmRef.scmRef().name());
            return fileSystem.mkdirs(result.toString()).map(dirCreated -> result);
        }

        private static final ObjectMapper MAPPER = JsonMapper.builder(new YAMLFactory()
                .disable(SPLIT_LINES)
                .disable(MINIMIZE_QUOTES)
                .enable(INDENT_ARRAYS_WITH_INDICATOR)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE))
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .addModule(new JavaTimeModule())
                .build().setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);

        @SuppressWarnings("unused")
        @Override
        public Uni<BuildReport> store(BuildReport buildReport) {
            return getOrCreateReportsDirectory(buildReport.buildRequest().buildGroup())
                    .onItem()
                    .transformToUni(reportDir -> {
                        Path buildRunReportDir = reportDir.resolve(format(buildReport.buildStart()));
                        return fileSystem.mkdirs(buildRunReportDir.toString())
                                .map(dirCreated -> buildRunReportDir);
                    }).onItem()
                    .transformToUni(buildRunReportDir -> Uni.createFrom().item(buildReport)
                            .emitOn(Infrastructure.getDefaultWorkerPool())
                            .map(pojo -> {
                                try {
                                    return new BytesAndFile(MAPPER.writeValueAsBytes(pojo),
                                            buildRunReportDir.resolve("build-report.yaml"));
                                } catch (Exception e) {
                                    throw new RuntimeException("Could not serialize pojo " + pojo, e);
                                }
                            }))
                    .onItem().transformToUni(bytesAndFile -> {
                        return fileSystem.writeFile(bytesAndFile.file().toString(), Buffer.buffer(bytesAndFile.bytes()));
                    })
                    .map(v -> buildReport);
        }

        @Override
        public Multi<BuildReport> list(BuildGroup buildGroup) {
            return getOrCreateReportsDirectory(buildGroup)
                    .onItem().transformToMulti(reportsDir -> {
                        return fileSystem.readDir(reportsDir.toString())
                                .onItem().transformToMulti(files -> Multi.createFrom().iterable(files))

                                .map(file -> reportsDir.resolve(file).resolve("build-report.yaml"))
                                .select().when(buildReportFile -> fileSystem.exists(buildReportFile.toString()))
                                .onItem().transformToUniAndMerge(buildReportFile -> {

                                    Uni<BuildReport> r = fileSystem.readFile(buildReportFile.toString())
                                            .map(buffer -> buffer.getBytes())
                                            .onItem().transformToUni(bytes -> Uni.createFrom().item(bytes)
                                                    .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                                                    .onItem().transform(bytes2 -> {
                                                        try {
                                                            return (BuildReport) MAPPER.readValue(bytes2, BuildReport.class);
                                                        } catch (IOException e) {
                                                            throw new UncheckedIOException(
                                                                    "Could not deserialize a BuildReport from "
                                                                            + new String(bytes2, StandardCharsets.UTF_8),
                                                                    e);
                                                        }
                                                    }));
                                    return r;
                                });
                    });
        }

        static record BytesAndFile(byte[] bytes, Path file) {

        }

    }
}
