/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.cliassured.CliAssured;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.os.Arch;
import org.l2x6.jrebuild.api.os.Os;
import org.l2x6.jrebuild.api.os.OsArch;

public class Shfmt {
    private static final Logger log = Logger.getLogger(Shfmt.class);
    private final String shfmtBinary;

    public Shfmt(Path cacheDir) {
        super();
        this.shfmtBinary = findOrInstallShfmtBinary(cacheDir);
    }

    static String findOrInstallShfmtBinary(Path cacheDir) {
        try {
            if (CliAssured.command("shfmt", "--help")
                    .then()
                    .stderr()
                    .hasLinesContaining("shfmt formats shell programs")
                    .execute()
                    .exitCode() == 0) {
                /* Use local shfmt installation if available */
                return "shfmt";
            }
        } catch (UncheckedIOException ignored) {
            /* shfmt is not installed */
        }

        /* Use the cached binary if available */
        final OsArch currentOsArch = OsArch.current();
        final ShfmtOsArchDetector detector = new ShfmtOsArchDetector();
        final Path cachedShfmtBinariesDir = cacheDir.resolve("shfmt");
        Optional<Path> shfmtBinary = findCachedBinary(currentOsArch, detector, cachedShfmtBinariesDir);
        if (shfmtBinary.isPresent()) {
            return shfmtBinary.toString();
        }
        /* Otherwise install it */
        return install(currentOsArch, detector, cachedShfmtBinariesDir);
    }

    static Optional<Path> findCachedBinary(final OsArch currentOsArch, final ShfmtOsArchDetector detector,
            final Path cachedShfmtBinariesDir) {
        if (!Files.isDirectory(cachedShfmtBinariesDir)) {
            return Optional.empty();
        }
        try (Stream<Path> bins = Files.list(cachedShfmtBinariesDir)) {
            return bins
                    .map(relFile -> cachedShfmtBinariesDir.resolve(relFile))
                    .filter(Files::isExecutable)
                    .filter(absFile -> {
                        String name = absFile.getFileName().toString();
                        OsArch osArch = detector.detect(name);
                        return currentOsArch.equals(osArch);
                    })
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + cachedShfmtBinariesDir, e);
        }
    }

    static String install(final OsArch currentOsArch, final ShfmtOsArchDetector detector,
            final Path cachedShfmtBinariesDir) {
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {

            String url = "https://api.github.com/repos/mvdan/sh/releases/latest";
            Builder lastReleaseReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET();
            final String ghToken = System.getenv("GITHUB_TOKEN");
            if (ghToken != null) {
                log.info("GITHUB_TOKEN set");
                lastReleaseReqBuilder.header("Authorization", "Bearer " + ghToken);
            } else {
                log.warn("Set GITHUB_TOKEN environment variable to test GitHub links");
            }
            HttpRequest lastReleaseReq = lastReleaseReqBuilder.build();
            HttpResponse<String> lastReleaseResp = client.send(lastReleaseReq, HttpResponse.BodyHandlers.ofString());
            if (lastReleaseResp.statusCode() != 200) {
                throw new IllegalStateException(
                        "Got " + lastReleaseResp.statusCode() + " " + lastReleaseResp.body() + " from " + url);
            }
            JsonNode root = new ObjectMapper().readTree(lastReleaseResp.body());
            JsonNode assets = root.get("assets");
            if (assets == null || !assets.isArray()) {
                throw new IllegalStateException("No assets found in " + url);
            }
            List<String> names = new ArrayList<>();
            for (JsonNode asset : assets) {
                String name = asset.get("name").asText();
                OsArch osArch = detector.detect(name);
                if (currentOsArch.equals(osArch)) {
                    String downloadUrl = asset.get("browser_download_url").asText();
                    String digest = asset.get("digest").asText();
                    Builder requestBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(downloadUrl))
                            .GET();
                    if (ghToken != null) {
                        requestBuilder.header("Authorization", "Bearer " + ghToken);
                    }
                    HttpRequest request = requestBuilder.build();

                    Files.createDirectories(cachedShfmtBinariesDir);
                    Path result = cachedShfmtBinariesDir.resolve(name);
                    HttpResponse<byte[]> response = client.send(
                            request,
                            HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("Got " + response.statusCode() + " from " + downloadUrl);
                    }

                    byte[] body = response.body();
                    assertDigest(body, digest);
                    Files.write(result, body);
                    if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                        Files.setPosixFilePermissions(result, PosixFilePermissions.fromString("rwxr-xr-x"));
                    }

                    return result.toString();
                }
                names.add(name);
            }
            throw new IllegalStateException("Could not find a binary matching " + currentOsArch + " among " + names);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not install shfmt", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Could not install shfmt", e);
        }
    }

    static void assertDigest(byte[] bytes, String expectedDigest) {
        String[] segments = expectedDigest.split("\\:");
        try {
            MessageDigest digest = MessageDigest.getInstance(segments[0]);
            digest.update(bytes);
            byte[] hashBytes = digest.digest();
            // Convert to hex
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            if (!hex.toString().equals(segments[1])) {
                throw new IllegalStateException();
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No such algarithm " + segments[0], e);
        }
    }

    public String parseToJson(String file) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CliAssured
                .command(shfmtBinary, "--to-json")
                .stdin(file)
                .then()
                .stdout()
                //.log(log::info)
                .redirect(baos)
                .execute()
                .assertSuccess();
        byte[] bytes = baos.toByteArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public String minify(String file) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CliAssured
                .command(shfmtBinary, "--minify")
                .stdin(file)
                .then()
                .stdout()
                //.log(log::info)
                .redirect(baos)
                .execute()
                .assertSuccess();
        byte[] bytes = baos.toByteArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static class ShfmtOsArchDetector {
        private final Map<String, Os> osMap;
        private final Map<String, Arch> archMap;

        public ShfmtOsArchDetector() {
            osMap = Map.of(
                    "linux", Os.LINUX,
                    "darwin", Os.MACOS,
                    "windows", Os.WINDOWS);

            archMap = new LinkedHashMap<>(); // iteration order matters due to arm64 and arm
            archMap.put("amd64", Arch.amd64);
            archMap.put("386", Arch.x86);
            archMap.put("arm64", Arch.arm64);
            archMap.put("arm", Arch.arm32);
        }

        public OsArch detect(String sfmtBinaryName) {
            Os os = osMap.entrySet().stream()
                    .filter(en -> sfmtBinaryName.contains(en.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("None of " + osMap.keySet() + " matches " + sfmtBinaryName));
            Arch arch = archMap.entrySet().stream()
                    .filter(en -> sfmtBinaryName.contains(en.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("None of " + archMap.keySet() + " matches " + sfmtBinaryName));
            return new OsArch(os, arch);
        }

    }
}
