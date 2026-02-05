/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.reproducible.central.ReproducibleCentralLayout.IndexedBuildspecRepository.Builder;
import org.l2x6.jrebuild.reproducible.central.api.Buildinfo;
import org.l2x6.jrebuild.reproducible.central.api.Buildspec;
import org.l2x6.jrebuild.reproducible.central.api.BuildspecRepository;
import org.l2x6.pom.tuner.model.Gav;

public class ReproducibleCentralLayout implements BuildspecRepository {
    private static final String BUILDSPEC_PROPERTIES_SUFFIX = "-buildspec.properties";
    private static final Logger log = Logger.getLogger(ReproducibleCentralLayout.class);
    private static final AtomicInteger instanceCounter = new AtomicInteger(0);

    private final Map<Gav, Buildspec> gavToBuildspec;

    public static BuildspecRepository cloneOrFetch(
            String remote,
            String branch,
            Path workingCopyDirectory,
            Path indexBaseDir) {

        final int threads = Math.max(Runtime.getRuntime().availableProcessors() / 2, 2);
        final ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
        final AtomicInteger threadCounter = new AtomicInteger(1);
        int instance = instanceCounter.getAndIncrement();
        final ExecutorService executor = new ThreadPoolExecutor(
                threads,
                threads,
                2L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> new Thread(threadGroup, r, instance == 0 ? ("repro-central-indexer-" + threadCounter.getAndIncrement())
                        : ("repro-central-indexer-" + instance + "-" + threadCounter.getAndIncrement())));

        final CompletableFuture<BuildspecRepository> delegate = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                final String centralContentCommitId;
                try (Git git = GitUtils.cloneOrFetchAndReset(remote, branch, workingCopyDirectory, 1)) {
                    final Ref ref = git.getRepository().exactRef("HEAD");
                    centralContentCommitId = ref.getObjectId().getName();
                }
                final Path centralContentDir = workingCopyDirectory.resolve("content");
                ReproducibleCentralLayout.load(executor, indexBaseDir, centralContentDir, centralContentCommitId, delegate);
            } catch (Throwable e) {
                delegate.completeExceptionally(e);
                executor.close();
            }
        });
        return new LazyBuildspecRepository(delegate);
    }

    static void load(ExecutorService executor, Path indexBaseDir, Path centralContentDir, String centralContentCommitId,
            CompletableFuture<BuildspecRepository> result) {
        final Path indexCommitDir = indexBaseDir.resolve(centralContentCommitId);
        if (Files.isDirectory(indexCommitDir)) {
            result.complete(IndexedBuildspecRepository.load(indexCommitDir));
        } else {
            index(executor, indexCommitDir, centralContentDir, result);
        }
    }

    static void index(ExecutorService executor, Path indexCommitDir, Path centralContentDir,
            CompletableFuture<BuildspecRepository> result) {
        log.infof("Parsing Reproducible Central Buildspecs from %s", centralContentDir);
        IndexedBuildspecRepository.Builder indexBuilder = new IndexedBuildspecRepository.Builder(indexCommitDir);
        final Map<Gav, Buildspec> gavToBuildspec = new ConcurrentHashMap<>();
        try (Stream<Path> files = Files.walk(centralContentDir)) {
            CompletableFuture[] futures = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".buildspec"))
                    .map(centralContentDir::resolve)
                    .map(p -> parse(executor, gavToBuildspec, p, indexBuilder))
                    .toArray(n -> new CompletableFuture[n]);

            CompletableFuture.allOf(futures)
                    .thenRun(() -> {
                        log.infof("Parsed Reproducible Central Buildspecs for %d GAVs", gavToBuildspec.size());
                        try {
                            indexBuilder.close();
                        } catch (Exception e) {
                            log.error("Could not close " + indexBuilder.getClass(), e);
                            try {
                                indexBuilder.delete();
                            } catch (Exception e1) {
                                log.error("Could not delete " + indexBuilder.getClass(), e1);
                            }
                        }
                        result.complete(new ReproducibleCentralLayout(Collections.unmodifiableMap(gavToBuildspec)));
                        executor.close();
                    })
                    .exceptionally(e -> {
                        indexBuilder.delete();
                        result.completeExceptionally(e);
                        return null;
                    });
        } catch (IOException e) {
            throw new RuntimeException(new UncheckedIOException("Could not walk " + centralContentDir, e));
        }
    }

    static CompletableFuture<Void> parse(ExecutorService executor, Map<Gav, Buildspec> gavToBuildspec, Path buildspecPath,
            Builder indexBuilder) {
        return CompletableFuture.runAsync(() -> {
            BuildinfoEntry entry = BuildinfoEntry.of(buildspecPath);
            Buildspec buildspec = entry.buildspec();
            Buildinfo buildinfo = entry.buildinfo();
            Set<Gav> gavs = buildinfo.gavs();
            for (Gav gav : gavs) {
                gavToBuildspec.put(gav, buildspec);
            }
            Gav gav = buildinfo.gav();
            //log.tracef("Parsed Buildspec for %s", gav);
            if (!gavs.contains(gav)) {
                gavToBuildspec.put(gav, buildspec);
            }
            indexBuilder.store(buildspec, gavs);
        }, executor);
    }

    ReproducibleCentralLayout(Map<Gav, Buildspec> gavToBuildspec) {
        this.gavToBuildspec = Objects.requireNonNull(gavToBuildspec);
    }

    public Buildspec lookup(Gav gav) {
        return gavToBuildspec.get(gav);
    }

    @Override
    public Set<Gav> gavs() {
        return gavToBuildspec.keySet();
    }

    private record BuildinfoEntry(Buildinfo buildinfo, Buildspec buildspec) {
        public static BuildinfoEntry of(Path buildspecPath) {
            final Buildspec buildspec = Buildspec.of(buildspecPath);
            final Path buildinfoPath = buildspecPath.getParent()
                    .resolve(buildspecPath.getFileName().toString().replace(".buildspec", ".buildinfo"));
            return new BuildinfoEntry(Buildinfo.of(buildinfoPath, buildspec.gav()), buildspec);
        }
    }

    public static class IndexedBuildspecRepository implements BuildspecRepository {
        private static final String GAV_TO_BUILDSPEC_PATH_PROPERTIES = "gavToBuildspecPath.properties";
        private final Map<Gav, String> gavToBuildspecPath;
        private final Path indexCommitDir;
        private final Map<String, Buildspec> pathToBuildspec = new ConcurrentHashMap<>();

        static IndexedBuildspecRepository load(Path indexCommitDir) {
            log.infof("Loading Reproducible Central Buildspecs from %s", indexCommitDir);
            final Map<Gav, String> gavToBuildspecPath = new HashMap<>();
            final Path gavPropsPath = indexCommitDir.resolve(GAV_TO_BUILDSPEC_PATH_PROPERTIES);
            Properties properties = new Properties() {
                @Override
                public Object put(Object key, Object value) {
                    return gavToBuildspecPath.put(Gav.of((String) key), (String) value);
                }
            };
            try (InputStream in = Files.newInputStream(gavPropsPath)) {
                properties.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + gavPropsPath, e);
            }
            log.infof("Loaded Reproducible Central Buildspecs from properties for %d GAVs", gavToBuildspecPath.size());
            return new IndexedBuildspecRepository(Collections.unmodifiableMap(gavToBuildspecPath), indexCommitDir);
        }

        public IndexedBuildspecRepository(Map<Gav, String> gavToBuildspecPath, Path indexCommitDir) {
            super();
            this.gavToBuildspecPath = gavToBuildspecPath;
            this.indexCommitDir = indexCommitDir;
        }

        @Override
        public Buildspec lookup(Gav gav) {
            final String relPath = gavToBuildspecPath.get(gav);
            if (relPath == null) {
                return null;
            }
            return pathToBuildspec.computeIfAbsent(relPath, this::load);
        }

        private Buildspec load(String relPath) {
            Path absPath = indexCommitDir.resolve(relPath);
            return Buildspec.fromProperties(loadProperties(absPath), absPath);
        }

        static Properties loadProperties(Path absPath) {
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(absPath)) {
                properties.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + absPath, e);
            }
            return properties;
        }

        @Override
        public Set<Gav> gavs() {
            return gavToBuildspecPath.keySet();
        }

        static class Builder implements AutoCloseable {
            private final Properties gavToBuildspecPath = new Properties();
            private final Path indexCommitDir;

            public Builder(Path indexCommitDir) {
                super();
                this.indexCommitDir = indexCommitDir;
            }

            public void delete() {
                if (!Files.exists(indexCommitDir)) {
                    return;
                }

                try {
                    Files.walkFileTree(indexCommitDir, new SimpleFileVisitor<>() {

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir,
                                IOException exc)
                                throws IOException {
                            if (exc != null) {
                                throw exc;
                            }
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not delete " + indexCommitDir, e);
                }
            }

            public void store(Buildspec buildspec, Collection<Gav> gavs) {
                Properties props = new Properties();
                buildspec.put(props);
                String relPath = buildspec.gav().getRepositoryPath() + BUILDSPEC_PROPERTIES_SUFFIX;

                Path absPath = indexCommitDir.resolve(relPath);
                if (!Files.isDirectory(absPath.getParent())) {
                    try {
                        Files.createDirectories(absPath.getParent());
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not create directory " + absPath.getParent(), e);
                    }
                }
                try (OutputStream out = Files.newOutputStream(absPath)) {
                    props.store(out, null);
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not write to " + absPath, e);
                }
                for (Gav gav : gavs) {
                    gavToBuildspecPath.put(gav.toString(), relPath);
                }
                Gav gav = buildspec.gav();
                if (!gavs.contains(gav)) {
                    gavToBuildspecPath.put(gav.toString(), relPath);
                }
            }

            @Override
            public void close() {
                final Path absPath = indexCommitDir.resolve(GAV_TO_BUILDSPEC_PATH_PROPERTIES);
                if (!Files.isDirectory(absPath.getParent())) {
                    try {
                        Files.createDirectories(absPath.getParent());
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not create directory " + absPath.getParent(), e);
                    }
                }
                try (OutputStream out = Files.newOutputStream(absPath)) {
                    gavToBuildspecPath.store(out, null);
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not write to " + absPath, e);
                }
            }

        }
    }

    public static class LazyBuildspecRepository implements BuildspecRepository {

        private final Future<BuildspecRepository> delegate;

        public LazyBuildspecRepository(Future<BuildspecRepository> delegate) {
            super();
            this.delegate = delegate;
        }

        BuildspecRepository awaitRecipeDirectory() {
            try {
                return delegate.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Buildspec lookup(Gav gav) {
            return awaitRecipeDirectory().lookup(gav);
        }

        @Override
        public Set<Gav> gavs() {
            return awaitRecipeDirectory().gavs();
        }

    }

}
