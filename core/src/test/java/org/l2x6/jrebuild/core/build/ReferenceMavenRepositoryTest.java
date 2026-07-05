/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import io.vertx.core.http.HttpServer;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.Gavtcf;

public class ReferenceMavenRepositoryTest {

    @Test
    void e2e() throws Exception {
        io.vertx.core.Vertx coreVertx = null;
        WebClient webClient = null;

        Path testRunDir = Path.of("target/ReferenceMavenRepositoryTest-" + UUID.randomUUID().toString()).toAbsolutePath()
                .normalize();
        Path remoteRepoDir = Path.of("target/test-classes/ReferenceMavenRepositoryTest/remote").toAbsolutePath().normalize();
        Files.createDirectories(remoteRepoDir);

        try {
            coreVertx = io.vertx.core.Vertx.vertx();

            HttpServer server = coreVertx.createHttpServer()
                    .requestHandler(req -> {
                        String path = req.path().substring(1);
                        Path file = remoteRepoDir.resolve(path);
                        if (Files.exists(file)) {
                            req.response()
                                    .putHeader("Content-Type", "application/octet-stream")
                                    .sendFile(file.toString());
                        } else {
                            req.response().setStatusCode(404).end();
                        }
                    })
                    .listen(0)
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get();

            final String referenceRepoBaseUri = "http://localhost:" + server.actualPort();
            final Vertx vertx = new Vertx(coreVertx);
            webClient = WebClient.create(vertx);

            Gavtc gavtc = Gavtc.of("org.l2x6.pom-tuner:pom-tuner:4.10.0:pom");
            byte[] artifactContent = Files.readAllBytes(remoteRepoDir.resolve(gavtc.getRepositoryPath()));
            String sha1HexString = sha1Hex(artifactContent);

            Assertions.assertThat(sha1HexString)
                    .isEqualTo(Files.readString(remoteRepoDir.resolve(gavtc.getRepositoryPath() + ".sha1")));

            Path localMavenRepo = createDir(testRunDir, "e2e-m2");
            Path localRefRepo = createDir(testRunDir, "e2e-ref");

            // Ensure both local repos are empty
            final String sha1RelPath = gavtc.getRepositoryPath() + ".sha1";
            Assertions.assertThat(localRefRepo.resolve(sha1RelPath)).doesNotExist();
            Assertions.assertThat(localRefRepo.resolve(gavtc.getRepositoryPath())).doesNotExist();
            Assertions.assertThat(localMavenRepo.resolve(sha1RelPath)).doesNotExist();
            Assertions.assertThat(localMavenRepo.resolve(gavtc.getRepositoryPath())).doesNotExist();

            ReferenceMavenRepository repo = new ReferenceMavenRepository(
                    referenceRepoBaseUri, localMavenRepo, localRefRepo, webClient, vertx.fileSystem());
            {
                Gavtcf result = repo.resolve(gavtc).await().indefinitely();

                // Check the effects in the local repos
                Assertions.assertThat(localRefRepo.resolve(gavtc.getRepositoryPath())).doesNotExist();
                Assertions.assertThat(localRefRepo.resolve(sha1RelPath))
                        .isRegularFile()
                        .hasContent(sha1HexString);

                Assertions.assertThat(localMavenRepo.resolve(gavtc.getRepositoryPath())).isRegularFile();
                Assertions.assertThat(localMavenRepo.resolve(sha1RelPath))
                        .isRegularFile()
                        .hasContent(sha1HexString);

                // Check the result

                assertResult(localMavenRepo, gavtc, artifactContent, result);
            }

            /* now remove the file from the remote to make sure the server is not hit when the file in local Maven repo
             * has correct sha1 */
            hide(remoteRepoDir, webClient, referenceRepoBaseUri, gavtc.getRepositoryPath());
            hide(remoteRepoDir, webClient, referenceRepoBaseUri, gavtc.getRepositoryPath() + ".sha1");
            /* The second attempt must pass without any of the files being available remotely */
            {
                final Gavtcf result = repo.resolve(gavtc).await().indefinitely();
                assertResult(localMavenRepo, gavtc, artifactContent, result);
            }

            /* Unhide the files in the remote repo */
            unhide(remoteRepoDir, webClient, referenceRepoBaseUri, gavtc.getRepositoryPath());
            unhide(remoteRepoDir, webClient, referenceRepoBaseUri, gavtc.getRepositoryPath() + ".sha1");

            /* Damage the artifact in local Maven repo */
            Files.writeString(localMavenRepo.resolve(gavtc.getRepositoryPath()), "foo bar");
            /* The file must be taken from localRefRepo */
            {
                final Gavtcf result = repo.resolve(gavtc).await().indefinitely();
                assertResult(localRefRepo, gavtc, artifactContent, result);
                Assertions.assertThat(localRefRepo.resolve(gavtc.getRepositoryPath())).hasBinaryContent(artifactContent);
            }
        } finally {

            if (webClient != null) {
                webClient.close();
            }
            if (coreVertx != null) {
                coreVertx.close().toCompletionStage().toCompletableFuture().get();
            }
        }

    }

    static void hide(Path remoteRepoDir, WebClient webClient, String referenceRepoBaseUri, String relPath) throws IOException {
        Files.move(remoteRepoDir.resolve(relPath), remoteRepoDir.resolve(relPath + ".hidden"));
        HttpResponse<Buffer> resp = webClient.getAbs(referenceRepoBaseUri + "/" + relPath).send().await().indefinitely();
        Assertions.assertThat(resp.statusCode()).isEqualTo(404);
    }

    static void unhide(Path remoteRepoDir, WebClient webClient, String referenceRepoBaseUri, String relPath)
            throws IOException {
        Files.move(remoteRepoDir.resolve(relPath + ".hidden"), remoteRepoDir.resolve(relPath));
        HttpResponse<Buffer> resp = webClient.getAbs(referenceRepoBaseUri + "/" + relPath).send().await().indefinitely();
        Assertions.assertThat(resp.statusCode()).isEqualTo(200);
    }

    private void assertResult(Path expectedRepo, Gavtc gavtc, byte[] expectedContent, Gavtcf actual) {
        actual.toGavtc().equals(gavtc);
        Assertions.assertThat(actual.getFile())
                .isEqualTo(expectedRepo.resolve(gavtc.getRepositoryPath()));
        Assertions.assertThat(actual.getFile())
                .hasBinaryContent(expectedContent);
    }

    static Path createDir(Path testRunDir, String path) throws IOException {
        Path result = testRunDir.resolve(path);
        Files.createDirectories(result);
        return result;
    }

    static String sha1Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
