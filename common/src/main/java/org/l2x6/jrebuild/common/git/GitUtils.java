/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common.git;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.FetchResult;
import org.jboss.logging.Logger;

public class GitUtils {
    private static final Logger log = Logger.getLogger(GitUtils.class);

    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    private static final long DELETE_RETRY_MILLIS = 5000L;

    private static final int CREATE_RETRY_COUNT = 256;

    public static Git cloneOrFetchAndReset(
            String remote,
            String branch,
            Path directory,
            int depth) {
        final Git git;
        if (Files.exists(directory.resolve(".git"))) {
            /* fetch and reset */
            git = openGit(directory);
            fetchAndReset(remote, branch, git);
        } else {
            /* Shallow clone */
            log.infof("Cloning %s to %s", remote, directory);
            try {
                git = Git.cloneRepository()
                        .setBranch(branch)
                        .setDirectory(directory.toFile())
                        //.setCredentialsProvider(new GitCredentials())
                        .setDepth(depth)
                        .setURI(remote)
                        .call();
            } catch (GitAPIException e) {
                throw new RuntimeException("Could not clone " + remote + " to " + directory, e);
            }
        }
        return git;
    }

    static Git openGit(Path dir) {
        try {
            return Git.open(dir.toFile());
        } catch (IOException e) {
            log.debug("No git repository in %s", dir, e);
        }
        try {
            ensureDirectoryExistsAndEmpty(dir);
            return Git.init().setDirectory(dir.toFile()).call();
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Could not open git repository in " + dir, e);
        }
    }

    static String fetchAndReset(String useUrl, String branch, Git git) {
        final Path dir = git.getRepository().getWorkTree().toPath();
        /* Forget local changes */
        try {
            Set<String> removedFiles = git.clean().setCleanDirectories(true).call();
            if (!removedFiles.isEmpty()) {
                log.warnf("Removed unstaged files %s", removedFiles);
            }
            git.reset().setMode(ResetType.HARD).call();
        } catch (Exception e) {
            log.warnf(e, "Could not forget local changes in %s", dir);
        }

        log.infof("Fetching recipe repo from %s to %s", useUrl, git.getRepository().getWorkTree());
        final String remoteAlias = "origin";
        try {
            ensureRemoteAvailable(useUrl, remoteAlias, git);

            final String remoteRef = "refs/heads/" + branch;
            final FetchResult fetchResult = git.fetch().setRemote(remoteAlias).setRefSpecs(remoteRef).call();
            final String remoteHead = fetchResult.getAdvertisedRef(remoteRef).getObjectId().getName();
            log.infof("Reseting the working copy to %s", remoteHead);
            /* Reset the domino-working-branch */
            git.branchCreate().setName(branch).setForce(true).setStartPoint(remoteHead).call();
            git.checkout().setName(branch).call();
            git.reset().setMode(ResetType.HARD).setRef(remoteHead).call();
            final Ref ref = git.getRepository().exactRef("HEAD");
            return ref.getObjectId().getName();
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Could not fetch and reset " + dir + " from " + useUrl, e);
        }
    }

    static void ensureRemoteAvailable(String useUrl, String remoteAlias, Git git) throws IOException {
        final StoredConfig config = git.getRepository().getConfig();
        boolean save = false;
        final String foundUrl = config.getString("remote", remoteAlias, "url");
        if (!useUrl.equals(foundUrl)) {
            config.setString("remote", remoteAlias, "url", useUrl);
            save = true;
        }
        final String foundFetch = config.getString("remote", remoteAlias, "fetch");
        final String expectedFetch = "+refs/heads/*:refs/remotes/" + remoteAlias + "/*";
        if (!expectedFetch.equals(foundFetch)) {
            config.setString("remote", remoteAlias, "fetch", expectedFetch);
            save = true;
        }
        if (save) {
            config.save();
        }
    }

    /**
     * If the given directory does not exist, creates it using {@link #ensureDirectoryExists(Path)}. Otherwise
     * recursively deletes all subpaths in the given directory.
     *
     * @param  dir         the directory to check
     * @throws IOException if the directory could not be created, accessed or its children deleted
     */
    static void ensureDirectoryExistsAndEmpty(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (DirectoryStream<Path> subPaths = Files.newDirectoryStream(dir)) {
                for (Path subPath : subPaths) {
                    if (Files.isDirectory(subPath)) {
                        deleteDirectory(subPath);
                    } else {
                        Files.delete(subPath);
                    }
                }
            }
        } else {
            ensureDirectoryExists(dir);
        }
    }

    /**
     * Makes sure that the given directory exists. Tries creating {@link #CREATE_RETRY_COUNT} times.
     *
     * @param  dir         the directory {@link Path} to check
     * @throws IOException if the directory could not be created or accessed
     */
    static void ensureDirectoryExists(Path dir) throws IOException {
        Throwable toThrow = null;
        for (int i = 0; i < CREATE_RETRY_COUNT; i++) {
            try {
                Files.createDirectories(dir);
                if (Files.exists(dir)) {
                    return;
                }
            } catch (AccessDeniedException e) {
                toThrow = e;
                /* Workaround for https://bugs.openjdk.java.net/browse/JDK-8029608 */
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                    toThrow = e1;
                }
            } catch (IOException e) {
                toThrow = e;
            }
        }
        if (toThrow != null) {
            throw new IOException(String.format("Could not create directory [%s]", dir), toThrow);
        } else {
            throw new IOException(
                    String.format("Could not create directory [%s] attempting [%d] times", dir, CREATE_RETRY_COUNT));
        }

    }

    /**
     * Deletes a file or directory recursively if it exists.
     *
     * @param  directory   the directory to delete
     * @throws IOException
     */
    static void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc == null) {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        // directory iteration failed; propagate exception
                        throw exc;
                    }
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (isWindows) {
                        final long deadline = System.currentTimeMillis() + DELETE_RETRY_MILLIS;
                        FileSystemException lastException = null;
                        do {
                            try {
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            } catch (FileSystemException e) {
                                lastException = e;
                            }
                        } while (System.currentTimeMillis() < deadline);
                        throw new IOException(String.format("Could not delete file %s after retrying for %d ms", file,
                                DELETE_RETRY_MILLIS), lastException);
                    } else {
                        Files.delete(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    // try to delete the file anyway, even if its attributes
                    // could not be read, since delete-only access is
                    // theoretically possible
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public static String uriToFileName(String uri) {
        return uri.replaceAll("^(http:|https:|git(\\+ssh)?:|ssh:|file:)/+", "")
                .replaceAll("^git@", "")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replace("-[\\-]+", "-")
                .replaceAll("^[-.]+", "")
                .replaceAll("[-.]+$", "")
                .replaceAll("\\.git$", "")
                .replaceAll("[-.]+$", "");
    }
}
