/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.maven;

import io.smallrye.mutiny.Uni;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.file.AsyncFile;
import io.vertx.mutiny.core.file.FileSystem;
import io.vertx.mutiny.core.streams.WriteStream;
import io.vertx.mutiny.ext.auth.prng.VertxContextPRNG;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.vertx.mutiny.ext.web.codec.BodyCodec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.l2x6.jrebuild.common.StackTraceLessException;
import org.l2x6.jrebuild.core.mutiny.MutinyConstants;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.Gavtcf;

/**
 * Resolves Maven artifacts against a remote reference repository, caching SHA1 hashes and artifact files locally.
 * <p>
 * Two local directories are used:
 * <ul>
 * <li>{@link #localMavenRepository} — the user's standard Maven local repository (e.g. {@code ~/.m2/repository}).
 * Artifacts downloaded here are written with Maven 3.9.x-compatible metadata so that Maven itself can recognize
 * them.</li>
 * <li>{@link #localReferenceMavenRepository} — a JRebuild-private directory used primarily for storing {@code .sha1}
 * hash files and, when the local Maven repository already contains a different version of an artifact, the reference
 * copy of that artifact.</li>
 * </ul>
 */
public class ReferenceMavenRepository {

    private static final Pattern HREF_PATTERN = Pattern.compile("\\s+href=\"([^\"]+)\"");

    /** Base URI of the remote reference Maven repository, e.g. {@code https://repo1.maven.org/maven2} */
    private final String referenceRepositorybaseUri;

    /** The root directory of local Maven repository, typically {@code ~/.m2/repository} */
    private final Path localMavenRepository;

    /**
     * The root directory of local reference Maven repository. This folder is private for JRebuild and
     * is used primarily for storing {@code sha1} hash files. The {@code *.pom}, {@code *-jar} atc. artifact files are
     * preferably
     * taken from {@link #localMavenRepository}.
     */
    private final Path localReferenceMavenRepository;

    /** Vert.x Mutiny {@link WebClient} used for all HTTP operations (downloading SHA1 files and artifacts) */
    private final WebClient webClient;

    /** Vert.x Mutiny {@link FileSystem} used for all non-blocking file I/O operations */
    private final FileSystem fileSystem;
    private final VertxContextPRNG prng;

    /**
     * @param referenceRepositorybaseUri    base URI of the remote reference Maven repository
     * @param localMavenRepository          root of the user's local Maven repository
     * @param localReferenceMavenRepository root of the JRebuild-private local reference repository
     * @param vertx                         Vert.x Mutiny
     */
    public ReferenceMavenRepository(String referenceRepositorybaseUri, Path localMavenRepository,
            Path localReferenceMavenRepository, Vertx vertx) {
        super();
        this.referenceRepositorybaseUri = referenceRepositorybaseUri;
        this.localMavenRepository = localMavenRepository;
        this.localReferenceMavenRepository = localReferenceMavenRepository;
        this.webClient = WebClient.create(vertx);
        this.fileSystem = vertx.fileSystem();
        this.prng = VertxContextPRNG.current(vertx);
    }

    /**
     * Attempts to GET the version URL of {@code gav} obtained through
     * {@code referenceRepositorybaseUri + "/"+ gav.getRepositoryPath())},
     * parses the returned HTML, extracts links pointing at the individual artifacts and tries to parse those to
     * {@link Gavtc}.
     *
     * @param  gav
     * @return
     */
    public Uni<List<Gavtc>> list(final Gav gav) {
        Path remoteArtifactsFile = localReferenceMavenRepository.resolve(gav.getRepositoryPath())
                .resolve("remote-artifacts.txt");
        return fileSystem.exists(remoteArtifactsFile.toString())
                .chain(exists -> {
                    if (exists) {
                        return fileSystem.readFile(remoteArtifactsFile.toString())
                                .map(buffer -> Stream.of(buffer.toString(StandardCharsets.UTF_8).split("\n"))
                                        .filter(line -> !line.isBlank()).map(Gavtc::of).toList());
                    } else {
                        String url = referenceRepositorybaseUri + "/" + gav.getRepositoryPath() + "/";
                        return webClient.getAbs(url)
                                .send().chain(resp -> {
                                    if (resp.statusCode() != 200) {
                                        return Uni.createFrom().failure(new HttpStatusException(resp.statusCode(),
                                                "Failed to download " + url + ": HTTP " + resp.statusCode()));
                                    }
                                    final List<Gavtc> result = new ArrayList<>();
                                    final StringJoiner joiner = new StringJoiner("\n");
                                    parseBody(gav, resp.bodyAsString(), gavtc -> {
                                        joiner.add(gavtc.toString());
                                        result.add(gavtc);
                                    });
                                    return fileSystem
                                            .mkdirs(remoteArtifactsFile.getParent().toString())
                                            .chain(v -> fileSystem
                                                    .writeFile(remoteArtifactsFile.toString(), Buffer.buffer(joiner.toString()))
                                                    .map(v2 -> result));
                                });
                    }
                });
    }

    /**
     * Does the following:
     * <ol>
     * <li>Checks whether the artifacts sha1 file is available in {@link #localReferenceMavenRepository} at
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")}.
     * <li>If it is not, it downloads it from
     * {@code referenceRepositorybaseUri +"/"+ gavtc.getRepositoryPath() + ".sha1"}
     * and stores it in {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")}.
     * <li>Checks whether {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath())} exists and whether
     * its
     * bytes have the same
     * sha1 hash as the sha1 stored in
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")}.
     * <li>If yes, return a {@link Gavtcf} constructed from {@code gavtc} and
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath())}
     * <li>If not, checks whether {@code localMavenRepository.resolve(gavtc.getRepositoryPath())} exists and whether its
     * bytes have the same
     * sha1 hash as the sha1 stored in
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")}.
     * <li>If yes, return a {@link Gavtcf} constructed from {@code gavtc} and
     * {@code localMavenRepository.resolve(gavtc.getRepositoryPath())}
     * <li>If {@code localMavenRepository.resolve(gavtc.getRepositoryPath())} does not exist, download it from
     * {@code referenceRepositorybaseUri +"/"+ gavtc.getRepositoryPath()}
     * and update all Maven metadata related to the freshly downloaded file, as if a recent Maven 3.9.x would download
     * it.
     * <li>If {@code localMavenRepository.resolve(gavtc.getRepositoryPath())} exists and but its bytes have a different
     * sha1 hash as the one stored in
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")},
     * then download
     * {@code referenceRepositorybaseUri +"/"+ gavtc.getRepositoryPath()} to
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath())}
     * and return a {@link Gavtcf} constructed from {@code gavtc} and
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath())}.
     * <li>Always uses Vert.x HTTP client for HTTP operations.
     *
     * @param  gavtc the {@link Gavtc} to resolve
     * @return       a {@link Uni} that emits a {@link Gavtcf} pointing to the resolved artifact file
     */
    public Uni<Gavtcf> resolve(final Gavtc gavtc) {
        final String repoPath = gavtc.getRepositoryPath();
        final Path sha1Path = localReferenceMavenRepository.resolve(repoPath + ".sha1");
        final Path refArtifactPath = localReferenceMavenRepository.resolve(repoPath);
        final Path localArtifactPath = localMavenRepository.resolve(repoPath);

        /* Step 1-2: Ensure the expected SHA1 hash is available locally (download from remote if missing) */
        return ensureSha1(sha1Path, repoPath)
                .chain(expectedSha1 ->
                /* Step 3-4: Check the reference repository for a cached artifact with a matching SHA1 */
                existsAndSha1Matches(refArtifactPath, expectedSha1)
                        .chain(refMatches -> {
                            if (refMatches.existsAndChecksumMatches()) {
                                return Uni.createFrom().item(gavtc.toGavtcf(refArtifactPath));
                            }
                            /* Step 5-6: Check the local Maven repository for an artifact with a matching SHA1 */
                            return existsAndSha1Matches(localArtifactPath, expectedSha1)
                                    .chain(localMatches -> {
                                        if (localMatches.existsAndChecksumMatches()) {
                                            return Uni.createFrom().item(gavtc.toGavtcf(localArtifactPath));
                                        }
                                        if (!localMatches.exists()) {
                                            Uni<Gavtcf> result = download(repoPath, localArtifactPath, expectedSha1)
                                                    .chain(() -> updateMavenMetadata(localArtifactPath,
                                                            expectedSha1))
                                                    .map(v -> gavtc.toGavtcf(localArtifactPath));
                                            /*
                                             * Step 7: Artifact is not in the local Maven repo at all —
                                             * download it there and write Maven metadata so Maven 3.9.x
                                             * treats it as a properly downloaded artifact
                                             */
                                            return result;
                                        }
                                        /*
                                         * Step 8: Artifact exists in the local Maven repo but has a
                                         * different SHA1 (e.g. a local rebuild or a different version).
                                         * We must not overwrite it, so download the reference copy into the
                                         * JRebuild-private reference repository instead.
                                         */
                                        return download(repoPath, refArtifactPath, expectedSha1)
                                                .map(v -> gavtc.toGavtcf(refArtifactPath));
                                    });
                        }));
    }

    static void parseBody(Gav gav, String body, Consumer<Gavtc> consumer) {
        Matcher m = HREF_PATTERN.matcher(body);
        final String prefix = gav.getArtifactId() + "-" + gav.getVersion();
        final Path pathBase = Path.of(gav.getRepositoryPath());
        while (m.find()) {
            String file = m.group(1);
            if (file.startsWith(prefix)
                    && !LocalMavenRepository.isChecksumOrSignature(file)) {
                Gavtc gavtc = Gavtc.of(pathBase.resolve(file));
                consumer.accept(gavtc);
            }
        }

    }

    /**
     * Checks whether the given file exists and its SHA1 hash matches the expected value.
     *
     * @param  path         the file to check
     * @param  expectedSha1 the expected 40-character hex SHA1 hash
     * @return              a {@link Uni} emitting {@code true} if the file exists and its SHA1 matches
     */
    Uni<ExistsAndChecksumMatches> existsAndSha1Matches(Path path, String expectedSha1) {
        return fileSystem.exists(path.toString())
                .chain(exists -> exists
                        ? sha1Matches(path, expectedSha1).chain(sha1Matches -> ExistsAndChecksumMatches.of(exists, sha1Matches))
                        : ExistsAndChecksumMatches.of(false, false));
    }

    /**
     * Returns the expected SHA1 hash for the given artifact, reading it from the local cache if available,
     * or downloading it from the remote reference repository and caching it locally.
     *
     * @param  sha1Path local path where the SHA1 file is (or will be) stored
     * @param  repoPath repository-relative path of the artifact (e.g. {@code org/example/foo/1.0/foo-1.0.jar})
     * @return          a {@link Uni} emitting the 40-character lowercase hex SHA1 hash
     */
    Uni<String> ensureSha1(Path sha1Path, String repoPath) {
        /* SHA1 already cached locally — read and return it */
        return fileSystem.exists(sha1Path.toString())
                .chain(exists -> {
                    if (exists) {
                        return readString(sha1Path).map(String::trim);
                    }

                    /* Download the SHA1 file from the remote reference repository and cache it locally */
                    final String sha1Url = referenceRepositorybaseUri + "/" + repoPath + ".sha1";
                    return webClient.getAbs(sha1Url).send()
                            .chain(resp -> {
                                if (resp.statusCode() != 200) {
                                    return Uni.createFrom().failure(new HttpStatusException(resp.statusCode(),
                                            "Failed to download " + sha1Url + ": HTTP " + resp.statusCode()));
                                }
                                final Buffer body = resp.body();
                                final String sha1 = body.toString().trim();
                                return fileSystem
                                        .mkdirs(sha1Path.getParent().toString())
                                        .chain(() -> fileSystem.writeFile(
                                                sha1Path.toString(),
                                                body))
                                        .replaceWith(sha1);
                            });
                });
    }

    /**
     * Downloads an artifact from the remote reference repository and writes it to {@code targetPath}.
     *
     * @param  repoPath   repository-relative path of the artifact
     * @param  targetPath local filesystem path to write the downloaded bytes to
     * @return            a {@link Uni} that completes when the download and write are finished
     */
    Uni<Void> download(String repoPath, Path targetPath, String expectedSha1) {
        Path tempTarget = targetPath
                .resolveSibling("_tmp." + Math.abs(prng.nextInt(10000)) + targetPath.getFileName().toString());
        return fileSystem
                .mkdirs(targetPath.getParent().toString())
                .chain(() -> fileSystem.open(tempTarget.toString(), MutinyConstants.WRITE_CREATE_OPTIONS))
                .onItem().transformToUni(asyncFile -> {
                    final String url = referenceRepositorybaseUri + "/" + repoPath;
                    final MessageDigest digest;
                    try {
                        digest = MessageDigest.getInstance("SHA-1");
                    } catch (NoSuchAlgorithmException e) {
                        return closeDeleteAndReturnFailure(tempTarget, asyncFile,
                                () -> new StackTraceLessException(e.getMessage()));
                    }
                    WriteStream<Buffer> sha1Stream = WriteStream
                            .newInstance(new DigestWriteStream(asyncFile.getDelegate(), digest));
                    return webClient.getAbs(url)
                            .as(BodyCodec.pipe(sha1Stream))
                            .send().chain(resp -> {
                                if (resp.statusCode() != 200) {
                                    return closeDeleteAndReturnFailure(tempTarget, asyncFile,
                                            () -> new HttpStatusException(resp.statusCode(),
                                                    "Failed to download " + url + ": HTTP " + resp.statusCode()));
                                }
                                final String actualSha1 = HexFormat.of().formatHex(digest.digest());
                                if (!actualSha1.equals(expectedSha1)) {
                                    return deleteAndReturnFailure(tempTarget, asyncFile,
                                            () -> new StackTraceLessException(
                                                    "SHA1 mismatch for " + url + ": expected " + expectedSha1
                                                            + " but got " + actualSha1));
                                }
                                return fileSystem.move(tempTarget.toString(), targetPath.toString(),
                                        MutinyConstants.COPY_ATTRIBUTES_REPLACE_ATOMIC_NOFOLLOW);
                            });
                });
    }

    Uni<Void> closeDeleteAndReturnFailure(Path file, AsyncFile asyncFile, Supplier<Throwable> exception) {
        return asyncFile.close()
                .chain(() -> deleteAndReturnFailure(file, asyncFile, exception));
    }

    Uni<Void> deleteAndReturnFailure(Path file, AsyncFile asyncFile, Supplier<Throwable> exception) {
        return fileSystem
                .delete(file.toString())
                .chain(() -> Uni.createFrom().failure(exception.get()));
    }

    /**
     * Writes Maven 3.9.x-compatible metadata next to a freshly downloaded artifact so that Maven itself
     * recognizes the artifact as properly downloaded. This includes:
     * <ul>
     * <li>A {@code .sha1} file containing the hex SHA1 hash of the artifact</li>
     * <li>An entry in the {@code _remote.repositories} file in the same directory, using the Maven Resolver
     * internal format</li>
     * </ul>
     *
     * @param  artifactPath path of the freshly downloaded artifact file
     * @param  sha1         the known SHA1 hash of the artifact
     * @return              a {@link Uni} that completes when all metadata has been written
     */
    Uni<Void> updateMavenMetadata(Path artifactPath, String sha1) {
        /* Write the .sha1 companion file (e.g. foo-1.0.jar.sha1) next to the artifact */
        final Path sha1File = artifactPath.resolveSibling(artifactPath.getFileName() + ".sha1");
        final Path remoteReposFile = artifactPath.getParent().resolve("_remote.repositories");
        final String fileName = artifactPath.getFileName().toString();
        final String entry = fileName + ">central=\n";

        return writeBytes(sha1File, sha1.getBytes(StandardCharsets.UTF_8))
                .chain(() -> fileSystem.exists(remoteReposFile.toString()))
                .chain(exists -> {
                    if (exists) {
                        /* Append only if this artifact is not already tracked */
                        return readString(remoteReposFile)
                                .chain(existing -> {
                                    if (!existing.contains(fileName + ">")) {
                                        return fileSystem.writeFile(remoteReposFile.toString(),
                                                Buffer.buffer(existing + entry));
                                    }
                                    return Uni.createFrom().voidItem();
                                });
                    }
                    /* Create the file with the standard header and the first entry */
                    final String header = "#NOTE: This is a Maven Resolver internal implementation file, its format can be changed without prior notice.\n"
                            + "#" + DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
                                    .format(ZonedDateTime.now())
                            + "\n";
                    return fileSystem.writeFile(remoteReposFile.toString(), Buffer.buffer(header + entry));
                });
    }

    /**
     * Checks whether the SHA1 hash of the given file matches the expected value.
     *
     * @param  file         the file to hash
     * @param  expectedSha1 the expected 40-character hex SHA1 hash
     * @return              a {@link Uni} emitting {@code true} if the file's SHA1 matches (case-insensitive)
     */
    Uni<Boolean> sha1Matches(Path file, String expectedSha1) {
        return computeSha1(file)
                .map(actual -> actual.equals(expectedSha1))
                .onFailure().recoverWithItem(false);
    }

    /**
     * Computes the SHA-1 hash of a file's contents.
     *
     * @param  file the file to hash
     * @return      a {@link Uni} emitting the 40-character lowercase hex SHA1 hash
     */
    Uni<String> computeSha1(Path file) {
        return fileSystem.open(file.toString(), MutinyConstants.READ_OPTIONS)
                .chain(asyncFile -> {
                    try {
                        final MessageDigest digest = MessageDigest.getInstance("SHA-1");
                        return asyncFile.toMulti()
                                .onItem().invoke(buffer -> digest.update(buffer.getBytes()))
                                .collect().last()
                                .replaceWith(() -> HexFormat.of().formatHex(digest.digest()))
                                .eventually(asyncFile::close);
                    } catch (NoSuchAlgorithmException e) {
                        return asyncFile.close().replaceWith(Uni.createFrom().<String> failure(e));
                    }
                });
    }

    /**
     * Writes the given bytes to a file, creating parent directories as needed.
     *
     * @param  path  the file to write
     * @param  bytes the content to write
     * @return       a {@link Uni} that completes when the write is finished
     */
    Uni<Void> writeBytes(Path path, byte[] bytes) {
        return fileSystem.mkdirs(path.getParent().toString())
                .chain(() -> fileSystem.writeFile(path.toString(), Buffer.buffer(bytes)));
    }

    /**
     * Reads the entire content of a file as a UTF-8 string.
     *
     * @param  path the file to read
     * @return      a {@link Uni} emitting the file content as a string
     */
    Uni<String> readString(Path path) {
        return fileSystem.readFile(path.toString())
                .map(buffer -> buffer.toString(StandardCharsets.UTF_8));
    }

    static class DigestWriteStream
            implements io.vertx.core.streams.WriteStream<io.vertx.core.buffer.Buffer> {
        private final io.vertx.core.file.AsyncFile delegate;
        private final MessageDigest digest;

        DigestWriteStream(io.vertx.core.file.AsyncFile delegate, MessageDigest digest) {
            this.delegate = delegate;
            this.digest = digest;
        }

        @Override
        public DigestWriteStream exceptionHandler(Handler<Throwable> handler) {
            delegate.exceptionHandler(handler);
            return this;
        }

        @Override
        public Future<Void> write(io.vertx.core.buffer.Buffer data) {
            digest.update(data.getBytes());
            return delegate.write(data);
        }

        @Override
        public void write(io.vertx.core.buffer.Buffer data, Handler<AsyncResult<Void>> handler) {
            digest.update(data.getBytes());
            delegate.write(data, handler);
        }

        @Override
        public void end(Handler<AsyncResult<Void>> handler) {
            delegate.end(handler);
        }

        @Override
        public DigestWriteStream setWriteQueueMaxSize(int maxSize) {
            delegate.setWriteQueueMaxSize(maxSize);
            return this;
        }

        @Override
        public boolean writeQueueFull() {
            return delegate.writeQueueFull();
        }

        @Override
        public DigestWriteStream drainHandler(Handler<Void> handler) {
            delegate.drainHandler(handler);
            return this;
        }
    }

    public static class HttpStatusException extends StackTraceLessException {
        private static final long serialVersionUID = 1L;
        private final int status;

        public HttpStatusException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }

    }

    static record ExistsAndChecksumMatches(boolean exists, boolean checksumMatches) {
        boolean existsAndChecksumMatches() {
            return exists && checksumMatches;
        }

        public static Uni<ExistsAndChecksumMatches> of(Boolean exists, Boolean sha1Matches) {
            return Uni.createFrom().item(new ExistsAndChecksumMatches(exists, sha1Matches));
        }
    }

}
