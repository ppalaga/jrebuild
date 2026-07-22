package org.l2x6.jrebuild.core.build.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.l2x6.jrebuild.api.scm.AnnotatedScmRepository;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.core.build.BuildReport;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout.CloneDirectory;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.SPLIT_LINES;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

public interface BuildReportStorage {
    Uni<BuildReport> store(BuildReport buildReport);

    Multi<BuildReport> list(FqScmRef fqScmRef);

    Uni<Void> close();

    static BuildReportStorage git(
            FileSystem fileSystem,
            String gitUri,
            String branch,
            String authorName,
            String authorEmail,
            CredentialsProvider credentialsProvider,
            int pushRetryCount,
            CloneDirectoriesLayout cloneDirectoriesLayout) {
        return new GitBuildReportStorage(fileSystem, gitUri, branch, authorName, authorEmail, credentialsProvider,
                pushRetryCount, cloneDirectoriesLayout);
    }

    /**
     */
    static BuildReportStorage local(FileSystem fileSystem, Path reportsDirectory) {
        return new FilesystemBuildReportStorage(fileSystem, reportsDirectory);
    }

    static class GitBuildReportStorage implements BuildReportStorage {
        private final Uni<GitFsStorage> delegate;
        private final String authorName;
        private final String authorEmail;
        private final int pushRetryCount;
        private final CredentialsProvider credentialsProvider;
        private FqScmRef remote;

        GitBuildReportStorage(
                FileSystem fileSystem,
                String gitUri,
                String branch,
                String authorName,
                String authorEmail,
                CredentialsProvider credentialsProvider,
                int pushRetryCount,
                CloneDirectoriesLayout cloneDirectoriesLayout) {
            this.authorName = authorName;
            this.authorEmail = authorEmail;
            this.pushRetryCount = pushRetryCount;
            this.credentialsProvider = credentialsProvider;
            this.remote = new FqScmRef(new ScmRef(Kind.BRANCH, branch, null), new AnnotatedScmRepository("?", "git", gitUri));
            this.delegate = cloneDirectoriesLayout
                    .lockDirectory(gitUri)
                    .chain(cloneDirectory -> GitUtils
                            .cloneOrFetchAndResetAsync(remote, cloneDirectory.cloneDirectory(), -1)
                            .map(git -> new GitFsStorage(
                                    cloneDirectory,
                                    git,
                                    new FilesystemBuildReportStorage(
                                            fileSystem,
                                            cloneDirectory.cloneDirectory()))))
                    .memoize().indefinitely();
        }

        @Override
        public Uni<BuildReport> store(BuildReport buildReport) {
            FqScmRef scmRef = buildReport.buildRequest().buildGroup().scmRef();
            final String message = buildReport.reproducibility() + ": " + scmRef.repository().uri() + "#"
                    + scmRef.scmRef().name();
            return delegate
                    .chain(gitFsStorage -> gitFsStorage.fsStorage
                            .store(buildReport)
                            .chain(report -> Uni.createFrom()
                                    .item(() -> {
                                        GitUtils.commit(gitFsStorage.git, message, authorName, authorEmail);
                                        GitUtils.push(gitFsStorage.git, remote.repository().uri(), credentialsProvider,
                                                pushRetryCount);
                                        return null;
                                    })
                                    .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()))
                            .replaceWith(buildReport));
        }

        @Override
        public Multi<BuildReport> list(FqScmRef fqScmRef) {
            return delegate.chain(gitFsStorage -> Uni.createFrom()
                    .item(() -> {
                        GitUtils.assertSuccess(GitUtils.rebase(gitFsStorage.git, remote.repository().uri()));
                        return gitFsStorage;
                    })
                    .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()))
                    .onItem().transformToMulti(gitFsStorage -> gitFsStorage.fsStorage.list(fqScmRef));
        }

        @Override
        public Uni<Void> close() {
            return delegate.chain(GitFsStorage::close);
        }

        record GitFsStorage(CloneDirectory cloneDirectory, Git git, FilesystemBuildReportStorage fsStorage) {
            public Uni<Void> close() {
                return Uni.createFrom()
                        .item(() -> {
                            try {
                                git.close();
                            } finally {
                                cloneDirectory.close();
                            }
                            return null;
                        })
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                        .replaceWithVoid();
            }
        }

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
        Uni<Path> getOrCreateReportsDirectory(FqScmRef fqScmRef) {
            Path result = reportsDirectory.resolve(GitUtils.uriToFileName(fqScmRef.repository().uri()))
                    .resolve(fqScmRef.scmRef().name());
            return fileSystem.mkdirs(result.toString()).map(dirCreated -> result);
        }

        private static final ObjectMapper MAPPER = JsonMapper.builder(new YAMLFactory()
                .disable(SPLIT_LINES)
                .enable(INDENT_ARRAYS_WITH_INDICATOR)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                // .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .addModule(new JavaTimeModule())
                .build().setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT);

        @SuppressWarnings("unused")
        @Override
        public Uni<BuildReport> store(BuildReport buildReport) {
            return getOrCreateReportsDirectory(buildReport.buildRequest().buildGroup().scmRef())
                    .onItem()
                    .transformToUni(buildReportDir -> Uni.createFrom()
                            .item(buildReport)
                            .emitOn(Infrastructure.getDefaultWorkerPool())
                            .map(pojo -> {
                                try {
                                    return new BytesAndFile(MAPPER.writeValueAsBytes(pojo),
                                            buildReportDir
                                                    .resolve("build-report-" + format(buildReport.buildStart()) + ".yaml"));
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
        public Multi<BuildReport> list(FqScmRef fqScmRef) {
            return getOrCreateReportsDirectory(fqScmRef)
                    .onItem().transformToMulti(reportsDir -> {
                        return fileSystem.readDir(reportsDir.toString())
                                .onItem().transformToMulti(files -> Multi.createFrom().iterable(files))
                                .select()
                                .where(buildReportFile -> {
                                    Path path = Path.of(buildReportFile);
                                    String fileName = path.getFileName().toString();
                                    return fileName.startsWith("build-report-")
                                            && fileName.endsWith(".yaml");
                                })
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

        @Override
        public Uni<Void> close() {
            return Uni.createFrom().voidItem();
        }

    }
}
