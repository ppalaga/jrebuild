/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.common.git.GitUtils;

class ReproducibleCentralBuildspecTest {
    private static final Logger log = Logger.getLogger(ReproducibleCentralBuildspecTest.class);

    private static final long DELETE_RETRY_MILLIS = 5000L;
    private static final int CREATE_RETRY_COUNT = 256;
    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    @Test
    void parseAll() throws IOException, InvalidRemoteException, TransportException, GitAPIException {
        final String remote = "file:///home/ppalaga/orgs/pnc/reproducible-central/.git";
        //final String remote = "https://github.com/jvm-repo-rebuild/reproducible-central.git";
        final String branch = "master";

        Path userHome = Path.of(System.getProperty("user.home"));
        Path cloneDir = userHome.resolve(".m2/buildspec");
        if (!Files.exists(cloneDir)) {
            cloneDir = userHome.resolve("target/clone-" + UUID.randomUUID());
            Files.createDirectories(cloneDir);
        }

        final Path reproCentralDir = cloneDir.resolve("reproducible-central");

        try (Git git = GitUtils.cloneOrFetchAndReset(remote, branch, reproCentralDir, 1)) {
        }

        final Path start = reproCentralDir.resolve("content");
        final AtomicInteger cnt = new AtomicInteger();
        try (Stream<Path> files = Files.walk(start)) {
            files
                    .parallel()
                    .filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".buildspec"))
                    .map(f -> start.resolve(f))
                    .peek(f -> cnt.incrementAndGet())
                    .peek(f -> log.infof("Parsing %s", f))
                    .forEach(f -> Buildspec.of(f));
        }
        log.infof("Parsed %d files under %s", cnt.get(), start);
    }
}
