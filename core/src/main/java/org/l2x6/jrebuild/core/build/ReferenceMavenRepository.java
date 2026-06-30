/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.file.FileSystem;
import io.vertx.mutiny.ext.web.client.WebClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
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

    /**
     * @param referenceRepositorybaseUri    base URI of the remote reference Maven repository
     * @param localMavenRepository          root of the user's local Maven repository
     * @param localReferenceMavenRepository root of the JRebuild-private local reference repository
     * @param webClient                     Vert.x Mutiny {@link WebClient} for HTTP operations
     * @param fileSystem                    Vert.x Mutiny {@link FileSystem} for non-blocking file I/O
     */
    public ReferenceMavenRepository(String referenceRepositorybaseUri, Path localMavenRepository,
            Path localReferenceMavenRepository, WebClient webClient, FileSystem fileSystem) {
        super();
        this.referenceRepositorybaseUri = referenceRepositorybaseUri;
        this.localMavenRepository = localMavenRepository;
        this.localReferenceMavenRepository = localReferenceMavenRepository;
        this.webClient = webClient;
        this.fileSystem = fileSystem;
    }

    /**
     * Does the following:
     * <ol>
     * <li>Checks whether the artifacts sha1 file is available in {@link #localReferenceMavenRepository} at
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")}.
     * <li>If it is not, it downloads it from {@code referenceRepositorybaseUri +"/"+ gavtc.getRepositoryPath() + ".sha1"}
     * and stores it in {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")}.
     * <li>Checks whether {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath())} exists and whether its
     * bytes have the same
     * sha1 hash as the sha1 stored in {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")}.
     * <li>If yes, return a {@link Gavtcf} constructed from {@code gavtc} and
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath())}
     * <li>If not, checks whether {@code localMavenRepository.resolve(gavtc.getRepositoryPath())} exists and whether its
     * bytes have the same
     * sha1 hash as the sha1 stored in {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")}.
     * <li>If yes, return a {@link Gavtcf} constructed from {@code gavtc} and
     * {@code localMavenRepository.resolve(gavtc.getRepositoryPath())}
     * <li>If {@code localMavenRepository.resolve(gavtc.getRepositoryPath())} does not exist, download it from
     * {@code referenceRepositorybaseUri +"/"+ gavtc.getRepositoryPath()}
     * and update all Maven metadata related to the freshly downloaded file, as if a recent Maven 3.9.x would download it.
     * <li>If {@code localMavenRepository.resolve(gavtc.getRepositoryPath())} exists and but its bytes have a different
     * sha1 hash as the one stored in {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")},
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
    public Uni<Gavtcf> resolve(Gavtc gavtc) {
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
                            if (refMatches) {
                                return Uni.createFrom().item(gavtc.toGavtcf(refArtifactPath));
                            }
                            /* Step 5-6: Check the local Maven repository for an artifact with a matching SHA1 */
                            return existsAndSha1Matches(localArtifactPath, expectedSha1)
                                    .chain(localMatches -> {
                                        if (localMatches) {
                                            return Uni.createFrom().item(gavtc.toGavtcf(localArtifactPath));
                                        }
                                        return fileSystem.exists(localArtifactPath.toString())
                                                .chain(localExists -> {
                                                    if (!localExists) {
                                                        /* Step 7: Artifact is not in the local Maven repo at all —
                                                         * download it there and write Maven metadata so Maven 3.9.x
                                                         * treats it as a properly downloaded artifact */
                                                        return download(repoPath, localArtifactPath)
                                                                .chain(() -> updateMavenMetadata(localArtifactPath,
                                                                        expectedSha1))
                                                                .map(v -> gavtc.toGavtcf(localArtifactPath));
                                                    }
                                                    /* Step 8: Artifact exists in the local Maven repo but has a
                                                     * different SHA1 (e.g. a local rebuild or a different version).
                                                     * We must not overwrite it, so download the reference copy into the
                                                     * JRebuild-private reference repository instead. */
                                                    return download(repoPath, refArtifactPath)
                                                            .map(v -> gavtc.toGavtcf(refArtifactPath));
                                                });
                                    });
                        }));
    }

    /**
     * Checks whether the given file exists and its SHA1 hash matches the expected value.
     *
     * @param  path         the file to check
     * @param  expectedSha1 the expected 40-character hex SHA1 hash
     * @return              a {@link Uni} emitting {@code true} if the file exists and its SHA1 matches
     */
    private Uni<Boolean> existsAndSha1Matches(Path path, String expectedSha1) {
        return fileSystem.exists(path.toString())
                .chain(exists -> exists ? sha1Matches(path, expectedSha1) : Uni.createFrom().item(false));
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
                        return readString(sha1Path).map(ReferenceMavenRepository::parseSha1);
                    }

                    /* Download the SHA1 file from the remote reference repository and cache it locally */
                    final String sha1Url = referenceRepositorybaseUri + "/" + repoPath + ".sha1";
                    return webClient.getAbs(sha1Url).send()
                            .chain(resp -> {
                                if (resp.statusCode() != 200) {
                                    throw new RuntimeException(
                                            "Failed to download " + sha1Url + ": HTTP " + resp.statusCode());
                                }
                                final String sha1 = parseSha1(resp.bodyAsString());
                                return writeBytes(sha1Path, sha1.getBytes(StandardCharsets.UTF_8))
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
    Uni<Void> download(String repoPath, Path targetPath) {
        final String url = referenceRepositorybaseUri + "/" + repoPath;
        return webClient.getAbs(url).send()
                .chain(resp -> {
                    if (resp.statusCode() != 200) {
                        throw new RuntimeException("Failed to download " + url + ": HTTP " + resp.statusCode());
                    }
                    return writeBytes(targetPath, resp.body().getBytes());
                });
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
                .map(actual -> actual.equalsIgnoreCase(expectedSha1))
                .onFailure().recoverWithItem(false);
    }

    /**
     * Computes the SHA-1 hash of a file's contents.
     *
     * @param  file the file to hash
     * @return      a {@link Uni} emitting the 40-character lowercase hex SHA1 hash
     */
    Uni<String> computeSha1(Path file) {
        return fileSystem.readFile(file.toString())
                .map(buffer -> {
                    try {
                        final MessageDigest digest = MessageDigest.getInstance("SHA-1");
                        return HexFormat.of().formatHex(digest.digest(buffer.getBytes()));
                    } catch (NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Extracts the 40-character hex SHA1 hash from a raw SHA1 file content string.
     * Handles both plain hashes ({@code abc123...}) and the {@code hash  filename} format
     * sometimes used by Maven repositories.
     *
     * @param  raw the raw content of a {@code .sha1} file
     * @return     the trimmed SHA1 hash (first whitespace-delimited token)
     */
    static String parseSha1(String raw) {
        return raw.trim().split("\\s+")[0];
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

}
