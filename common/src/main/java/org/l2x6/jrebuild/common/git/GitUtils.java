/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common.git;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.DepthWalk.RevWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.FqScmRef.FqScmRefRecord;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository.ScmRepositoryRecord;
import org.l2x6.jrebuild.common.StackTraceLessException;

public class GitUtils {
    private static final Logger log = Logger.getLogger(GitUtils.class);

    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    private static final long DELETE_RETRY_MILLIS = 5000L;

    private static final int CREATE_RETRY_COUNT = 256;

    /**
     * @param  fqScmRef
     * @param  directory
     * @param  depth     values lower or equal to {@code 0} mean unbounded depth
     * @return
     */
    public static Git cloneOrFetchAndReset(
            FqScmRef fqScmRef,
            Path directory,
            int depth) {
        if (!fqScmRef.repository().isGit()) {
            throw new IllegalArgumentException("Can handle only git repositories; found " + fqScmRef);
        }
        final Git git;
        if (Files.exists(directory.resolve(".git"))) {
            /* fetch and reset */
            git = openGit(directory);
            fetchAndReset(fqScmRef, git);
        } else {
            /* Shallow clone */
            log.infof("Cloning %s to %s", fqScmRef.repository().uri(), directory);
            try {
                CloneCommand cloneCommand = Git.cloneRepository()
                        .setBranch(fqScmRef.scmRef().name())
                        .setDirectory(directory.toFile())
                // .setCredentialsProvider(new GitCredentials())
                ;
                if (depth > 0) {
                    cloneCommand.setDepth(depth);
                }
                git = cloneCommand
                        .setURI(fqScmRef.repository().uri())
                        .call();
            } catch (GitAPIException e) {
                throw new RuntimeException("Could not clone " + fqScmRef.repository().uri() + " to " + directory, e);
            }
        }
        return git;
    }

    public static Uni<Git> cloneOrFetchAndResetAsync(
            FqScmRef fqScmRef,
            Path directory,
            int depth) {
        return Uni.createFrom().item(() -> cloneOrFetchAndReset(fqScmRef, directory, depth))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    public static Git cloneOrFetchAndReset(
            String remoteUri,
            String branch,
            Path directory,
            int depth) {
        FqScmRefRecord fqScmRef = new FqScmRefRecord(new ScmRef(Kind.BRANCH, branch, null),
                new ScmRepositoryRecord("git", remoteUri));
        return cloneOrFetchAndReset(fqScmRef, directory, depth);
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

    static String fetchAndReset(FqScmRef fqScmRef, Git git) {
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

        String uri = fqScmRef.repository().uri();
        log.infof("Fetching recipe repo from %s to %s", uri, git.getRepository().getWorkTree());
        final String remoteAlias = "origin";
        try {
            ensureRemoteAvailable(uri, remoteAlias, git);

            ScmRef scmRef = fqScmRef.scmRef();
            final String remoteRef = scmRef.refSpec();
            final FetchResult fetchResult = git.fetch().setRemote(remoteAlias).setRefSpecs(remoteRef).call();
            final String remoteHead = fetchResult.getAdvertisedRef(remoteRef).getObjectId().getName();
            log.infof("Reseting the working copy to %s", remoteHead);
            String branch = scmRef.kind() == Kind.BRANCH ? scmRef.name() : scmRef.name() + "-jrebuild-branch";
            if (git.getRepository().findRef("refs/heads/" + branch) == null) {
                git.branchCreate().setName(branch).setForce(true).setStartPoint(remoteHead).call();
            }
            git.checkout().setName(branch).call();
            git.reset().setMode(ResetType.HARD).setRef(remoteHead).call();
            final Ref ref = git.getRepository().exactRef("HEAD");
            return ref.getObjectId().getName();
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Could not fetch and reset " + dir + " from " + uri, e);
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

    public static Path resolveUriToFilePath(Path parent, String uri) {
        Path result = parent.resolve(uriToFilePath(uri)).normalize();
        if (!result.startsWith(parent)) {
            throw new IllegalStateException(
                    "Could not safely transform URI " + uri + " to a subpath of " + parent + ". Resulting path: " + result);
        }
        return result;
    }

    static Path uriToFilePath(String uri) {
        uri = uri
                .replaceAll("^(git(\\+ssh)?:|ssh:)//git@", "git@")
                .replaceAll("(^file:.*)[/\\\\].git[/\\\\]?", "$1");

        try {
            URIish urish = new URIish(uri);
            StringJoiner sb = new StringJoiner("/");
            Stream.<Supplier<String>> of(urish::getHost, urish::getPath)
                    .map(Supplier::get)
                    .filter(Objects::nonNull)
                    .map(GitUtils::trimSlash)
                    .forEach(sb::add);

            String sanitized = sb.toString().replace('\\', '/')
                    .replace("/../", "/")
                    .replaceAll("^\\.\\./", "")
                    .replaceAll("[^A-Za-z0-9._/-]+", "-")
                    .replace("-[\\-]+", "-")
                    .replaceAll("^[-.]+", "")
                    .replaceAll("[-.]+$", "")
                    .replaceAll("[^A-Za-z0-9_]+/", "/")
                    .replaceAll("/[^A-Za-z0-9_]+", "/")
                    .replaceAll("\\.git$", "")
                    .replaceAll("[-.]+$", "");
            return Path.of(sanitized).normalize();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Could not parse git URI " + uri, e);
        }
    }

    static String trimSlash(String string) {
        return string.replaceAll("^[/\\\\]+", "").replaceAll("[/\\\\]+$", "");
    }

    public static Uni<RevCommit> commitAsync(Git git, String message, String authorName, String authorEmail)
            throws NoFilepatternException, GitAPIException {
        return Uni.createFrom().item(() -> commit(git, message, authorName, authorEmail))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    public static RevCommit commit(Git git, String message, String authorName, String authorEmail) {
        try {
            git.add()
                    .setAll(true)
                    .call();

            return git.commit()
                    .setMessage(message)
                    .setAuthor(authorName, authorEmail)
                    .call();
        } catch (GitAPIException e) {
            throw new RuntimeException("Could not commit", e);
        }
    }

    public static void push(Git git, String remoteUri, CredentialsProvider credentialsProvider, int retryCount) {

        try {
            String branch = git.getRepository().getBranch();
            int i = 0;
            for (; i < retryCount; i++) {
                String refSpec = "refs/heads/" + branch;
                Iterable<PushResult> results = git.push()
                        .setRemote(remoteUri)
                        .add(refSpec)
                        .setCredentialsProvider(credentialsProvider)
                        .call();

                if (pushSuccessful(results)) {
                    return;
                }
                if (i + 1 >= retryCount) {
                    throw new IllegalStateException("Retry count " + retryCount + " exceeded");
                }

                assertSuccess(rebase(git, remoteUri));

                /* ... and try again */
            }
            if (i >= retryCount) {
                throw new IllegalStateException("Retry count " + retryCount + " exceeded");
            }
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Could not push, fetch or rebase", e);
        }
    }

    public static void assertSuccess(RebaseResult rebase) {
        switch (rebase.getStatus()) {
        case RebaseResult.Status.OK:
        case RebaseResult.Status.UP_TO_DATE:
        case RebaseResult.Status.FAST_FORWARD:
            return;
        default:
            throw new IllegalStateException("Unexpected RebaseResult status: " + rebase.getStatus());
        }

    }

    public static RebaseResult rebase(Git git, String remoteUri) {

        try {
            String branch = git.getRepository().getBranch();
            String refSpec = "refs/heads/" + branch;
            final FetchResult fetchResult = git.fetch().setRemote(remoteUri).setRefSpecs(refSpec).call();
            final ObjectId remoteHead = fetchResult.getAdvertisedRef(refSpec).getObjectId();
            return git.rebase()
                    .setUpstream(remoteHead)
                    .call();
        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Could not rebase from " + remoteUri, e);
        }
    }

    public static Uni<ZonedDateTime> lastCommitDate(Git git) {
        return Uni.createFrom().item(() -> {
            Repository repo = git.getRepository();
            try (RevWalk walk = new RevWalk(repo, 1)) {
                ObjectId head = repo.resolve("HEAD");
                int commitTimeSeconds = walk.parseCommit(head).getCommitTime();
                return ZonedDateTime.ofInstant(Instant.ofEpochSecond(commitTimeSeconds), ZoneOffset.UTC);
            } catch (IOException e) {
                throw new StackTraceLessException("Could not find the date of the last commit in " + repo.getWorkTree(), e);
            }
        });
    }

    static boolean pushSuccessful(Iterable<PushResult> results) {
        for (PushResult result : results) {
            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                switch (update.getStatus()) {
                case RemoteRefUpdate.Status.OK:
                case RemoteRefUpdate.Status.UP_TO_DATE:
                    continue; // All ref updates must be OK or UP_TO_DATE
                case RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED:
                case RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD:
                    // We will try to rebase
                    return false;
                default:
                    throw new IllegalArgumentException("Unexpected RemoteRefUpdate: " + update + " for " + result);
                }
            }
        }
        return true;
    }

    public static Optional<Path> findRepoRootDirectory(Path dir) {
        File gitDir = new FileRepositoryBuilder().findGitDir(dir.toFile()).getGitDir();
        return gitDir != null ? Optional.of(gitDir.toPath()) : Optional.empty();
    }
}
