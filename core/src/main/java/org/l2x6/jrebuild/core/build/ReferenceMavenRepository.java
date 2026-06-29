/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.ext.web.client.WebClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.Gavtcf;

public class ReferenceMavenRepository {
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

    private final WebClient webClient;

    public ReferenceMavenRepository(String referenceRepositorybaseUri, Path localMavenRepository,
            Path localReferenceMavenRepository, WebClient webClient) {
        super();
        this.referenceRepositorybaseUri = referenceRepositorybaseUri;
        this.localMavenRepository = localMavenRepository;
        this.localReferenceMavenRepository = localReferenceMavenRepository;
        this.webClient = webClient;
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
     * @return
     */
    public Uni<Gavtcf> resolve(Gavtc gavtc) {
        final String repoPath = gavtc.getRepositoryPath();
        final Path sha1Path = localReferenceMavenRepository.resolve(repoPath + ".sha1");

        return ensureSha1(sha1Path, repoPath)
                .chain(expectedSha1 -> {
                    final Path refArtifactPath = localReferenceMavenRepository.resolve(repoPath);
                    if (Files.exists(refArtifactPath) && sha1Matches(refArtifactPath, expectedSha1)) {
                        return Uni.createFrom().item(gavtc.toGavtcf(refArtifactPath));
                    }

                    final Path localArtifactPath = localMavenRepository.resolve(repoPath);
                    if (Files.exists(localArtifactPath) && sha1Matches(localArtifactPath, expectedSha1)) {
                        return Uni.createFrom().item(gavtc.toGavtcf(localArtifactPath));
                    }

                    if (!Files.exists(localArtifactPath)) {
                        return download(repoPath, localArtifactPath)
                                .invoke(() -> updateMavenMetadata(localArtifactPath, expectedSha1))
                                .map(v -> gavtc.toGavtcf(localArtifactPath));
                    }

                    return download(repoPath, refArtifactPath)
                            .map(v -> gavtc.toGavtcf(refArtifactPath));
                });
    }

    Uni<String> ensureSha1(Path sha1Path, String repoPath) {
        if (Files.exists(sha1Path)) {
            return Uni.createFrom().item(() -> parseSha1(readString(sha1Path)));
        }
        final String sha1Url = referenceRepositorybaseUri + "/" + repoPath + ".sha1";
        return webClient.getAbs(sha1Url).send()
                .map(resp -> {
                    if (resp.statusCode() != 200) {
                        throw new RuntimeException("Failed to download " + sha1Url + ": HTTP " + resp.statusCode());
                    }
                    final String sha1 = parseSha1(resp.bodyAsString());
                    writeBytes(sha1Path, sha1.getBytes(StandardCharsets.UTF_8));
                    return sha1;
                });
    }

    Uni<Void> download(String repoPath, Path targetPath) {
        final String url = referenceRepositorybaseUri + "/" + repoPath;
        return webClient.getAbs(url).send()
                .map(resp -> {
                    if (resp.statusCode() != 200) {
                        throw new RuntimeException("Failed to download " + url + ": HTTP " + resp.statusCode());
                    }
                    writeBytes(targetPath, resp.body().getBytes());
                    return null;
                });
    }

    static void updateMavenMetadata(Path artifactPath, String sha1) {
        final Path sha1File = artifactPath.resolveSibling(artifactPath.getFileName() + ".sha1");
        writeBytes(sha1File, sha1.getBytes(StandardCharsets.UTF_8));

        final Path remoteReposFile = artifactPath.getParent().resolve("_remote.repositories");
        final String fileName = artifactPath.getFileName().toString();
        final String entry = fileName + ">central=\n";
        try {
            if (Files.exists(remoteReposFile)) {
                final String existing = Files.readString(remoteReposFile, StandardCharsets.UTF_8);
                if (!existing.contains(fileName + ">")) {
                    Files.writeString(remoteReposFile, entry, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                }
            } else {
                final String header = "#NOTE: This is a Maven Resolver internal implementation file, its format can be changed without prior notice.\n"
                        + "#" + DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
                                .format(ZonedDateTime.now())
                        + "\n";
                Files.writeString(remoteReposFile, header + entry, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean sha1Matches(Path file, String expectedSha1) {
        try {
            return computeSha1(file).equalsIgnoreCase(expectedSha1);
        } catch (UncheckedIOException e) {
            return false;
        }
    }

    static String computeSha1(Path file) {
        try {
            final byte[] bytes = Files.readAllBytes(file);
            final MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static String parseSha1(String raw) {
        return raw.trim().split("\\s+")[0];
    }

    static void writeBytes(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
