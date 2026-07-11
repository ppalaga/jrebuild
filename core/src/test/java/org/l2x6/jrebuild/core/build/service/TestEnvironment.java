package org.l2x6.jrebuild.core.build.service;

import io.vertx.core.http.HttpServer;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout;
import org.l2x6.pom.tuner.model.Gavtc;

public class TestEnvironment implements AutoCloseable {
    private final io.vertx.core.Vertx coreVertx;
    private final WebClient webClient;

    private final Path testRunDir;
    private final Path remoteRepoDir;
    private final String referenceRepoBaseUri;
    private final Path localMavenRepo;
    private final Path localRefRepo;
    private final Vertx vertx;
    private ReferenceMavenRepository referenceMavenRepository;
    private FindReferenceArtifactsService findReferenceArtifactsService;
    private final Path clonesDir;
    private CloneDirectoriesLayout cloneDirectoriesLayout;

    public TestEnvironment(Class<?> testClass) {
        String testName = testClass.getSimpleName();
        testRunDir = Path.of("target/" + testName + "-" + UUID.randomUUID().toString()).toAbsolutePath()
                .normalize();
        remoteRepoDir = createDir(testRunDir, "remote");
        clonesDir = createDir(testRunDir, "clones");
        localMavenRepo = createDir(testRunDir, "m2");
        localRefRepo = createDir(testRunDir, "ref");

        Path remoteSource = Path.of("target/test-classes/ReferenceMavenRepositoryTest/remote").toAbsolutePath().normalize();
        try {
            Files.walkFileTree(remoteSource, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path targetDir = remoteRepoDir.resolve(remoteSource.relativize(dir));
                    Files.createDirectories(targetDir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path targetFile = remoteRepoDir.resolve(remoteSource.relativize(file));
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not walk " + remoteSource, e);
        }
        coreVertx = io.vertx.core.Vertx.vertx();

        try {
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

            referenceRepoBaseUri = "http://localhost:" + server.actualPort();
            vertx = new Vertx(coreVertx);
            webClient = WebClient.create(vertx);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

    }

    public Path createLargeRemoteFile(Gavtc gavtc, int sizeBytes) throws IOException, NoSuchAlgorithmException {
        Path gavtcTxtPath = remoteRepoDir.resolve(gavtc.getRepositoryPath());
        Files.createDirectories(gavtcTxtPath.getParent());

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        int cnt = 0;
        byte[] buffer = "0123456789abcdef".getBytes(StandardCharsets.ISO_8859_1);
        try (OutputStream out = Files.newOutputStream(gavtcTxtPath)) {
            while (cnt < sizeBytes) {
                out.write(buffer);
                cnt += buffer.length;
                digest.update(buffer);
            }
        }
        Path sha1Path = gavtcTxtPath.getParent().resolve("pom-tuner-4.10.0.txt.sha1");
        Files.writeString(sha1Path,
                HexFormat.of().formatHex(digest.digest()));
        return sha1Path;
    }

    @Override
    public void close() throws Exception {
        if (webClient != null) {
            webClient.close();
        }
        if (coreVertx != null) {
            coreVertx.close().toCompletionStage().toCompletableFuture().get();
        }

    }

    static Path createDir(Path testRunDir, String path) {
        Path result = testRunDir.resolve(path);
        try {
            Files.createDirectories(result);
            return result.toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + result, e);
        }
    }

    public Path remoteRepoDirectory() {
        return remoteRepoDir;
    }

    public Path m2Directory() {
        return localMavenRepo;
    }

    public Path localRefRepoDirectory() {
        return localRefRepo;
    }

    public ReferenceMavenRepository getReferenceMavenRepository() {
        if (referenceMavenRepository == null) {
            referenceMavenRepository = new ReferenceMavenRepository(
                    referenceRepoBaseUri, localMavenRepo, localRefRepo, webClient, vertx.fileSystem());
        }
        return referenceMavenRepository;
    }

    public FindReferenceArtifactsService getFindReferenceArtifactsService() {
        if (findReferenceArtifactsService == null) {
            findReferenceArtifactsService = new FindReferenceArtifactsService(getCloneDirectoriesLayout(),
                    getReferenceMavenRepository());
        }
        return findReferenceArtifactsService;

    }

    public CloneDirectoriesLayout getCloneDirectoriesLayout() {
        if (cloneDirectoriesLayout == null) {
            cloneDirectoriesLayout = new CloneDirectoriesLayout(clonesDir);
        }
        return cloneDirectoriesLayout;
    }

    public void hideRemote(String relPath) {
        Path src = remoteRepoDir.resolve(relPath);
        Path dest = remoteRepoDir.resolve(relPath + ".hidden");
        try {
            Files.move(src, dest);
            HttpResponse<Buffer> resp = webClient.getAbs(referenceRepoBaseUri + "/" + relPath).send().await().indefinitely();
            Assertions.assertThat(resp.statusCode()).isEqualTo(404);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not move " + src + " -> " + dest, e);
        }
    }

    public void unhideRemote(String relPath)
            throws IOException {
        Path src = remoteRepoDir.resolve(relPath + ".hidden");
        Path dest = remoteRepoDir.resolve(relPath);
        try {
            Files.move(src, dest);
            HttpResponse<Buffer> resp = webClient.getAbs(referenceRepoBaseUri + "/" + relPath).send().await().indefinitely();
            Assertions.assertThat(resp.statusCode()).isEqualTo(200);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not move " + src + " -> " + dest, e);
        }
    }

    public String referenceRepoBaseUri() {
        return referenceRepoBaseUri;
    }
}
