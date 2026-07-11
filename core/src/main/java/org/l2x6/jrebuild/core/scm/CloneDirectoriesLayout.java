/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.l2x6.jrebuild.common.git.GitUtils;

public class CloneDirectoriesLayout {
    private final Path clonesRootDirectory;
    private final Set<Path> localLocks;

    public CloneDirectoriesLayout(Path clonesRootDirectory) {
        super();
        this.clonesRootDirectory = clonesRootDirectory;
        this.localLocks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    public Uni<CloneDirectory> lockDirectory(String scmUri) {
        final Path repoDir = clonesRootDirectory.resolve(GitUtils.uriToFileName(scmUri)).toAbsolutePath().normalize();
        return Uni.createFrom().item(() -> {
            return lock(repoDir);
        })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    CloneDirectory lock(Path groupDir) {
        try {
            Files.createDirectories(groupDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + groupDir, e);
        }
        final Path locksFile = groupDir.resolve("clones.lock");
        FileChannel lockFile = null;
        try {
            lockFile = FileChannel.open(locksFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            for (int i = 0; i <= Integer.MAX_VALUE; i++) {
                FileLock fileLock = lockFile.tryLock(i, 1, false);
                if (fileLock != null) {
                    Path lockedDir = groupDir.resolve(String.valueOf(i));
                    if (localLocks.add(lockedDir)) {
                        try {
                            Files.createDirectories(lockedDir);
                        } catch (IOException e) {
                            throw new UncheckedIOException("Could not create " + lockedDir, e);
                        }
                        return new CloneDirectory(lockedDir, lockFile);
                    }
                }
            }
        } catch (Exception e) {
            if (lockFile != null) {
                try {
                    lockFile.close();
                } catch (IOException ignored) {
                }
            }
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("Could not lock any subdirectory of " + groupDir);
    }

    public class CloneDirectory implements AutoCloseable {
        private final Path lockedDirectory;
        private final FileChannel lockFile;

        public CloneDirectory(Path lockedDirectory, FileChannel lockFile) {
            super();
            this.lockedDirectory = lockedDirectory;
            this.lockFile = lockFile;
        }

        public Path cloneDirectory() {
            return lockedDirectory.resolve("clone");
        }

        public Path deployDirectory() {
            return lockedDirectory.resolve("deploy");
        }

        @Override
        public void close() {
            localLocks.remove(lockedDirectory);
            try {
                lockFile.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }
}
