/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.reproducible.central.api.Buildinfo;
import org.l2x6.jrebuild.reproducible.central.api.Buildspec;
import org.l2x6.jrebuild.reproducible.central.api.BuildspecRepository;
import org.l2x6.pom.tuner.model.Gav;

public class ReproducibleCentralLayout implements BuildspecRepository {

    private final Path baseDir;

    private final Map<Gav, Buildspec> gavToBuildspec = new ConcurrentHashMap<>();
    private final Map<String, List<BuildinfoEntry>> groupIdToBuildinfos = new ConcurrentHashMap<>();
    private final Map<String, BuildinfoEntry> relPathToBuildinfoEntry = new ConcurrentHashMap<>();

    public ReproducibleCentralLayout(Path baseDir) {
        super();
        this.baseDir = baseDir;
    }

    public Buildspec lookup(Gav gav) {
        return gavToBuildspec.computeIfAbsent(gav, k -> findBuildspec(k));
    }

    Buildspec findBuildspec(Gav gav) {
        final List<BuildinfoEntry> entries = groupIdToBuildinfos.computeIfAbsent(gav.getGroupId(),
                k -> listBuildinfoEntries(k));
        return entries.stream()
                .filter(en -> en.buildinfo().gavs().contains(gav))
                .map(BuildinfoEntry::buildspec)
                .findFirst()
                .orElse(null);
    }

    private List<BuildinfoEntry> listBuildinfoEntries(String groupId) {
        final List<BuildinfoEntry> result = new ArrayList<>();
        final Path groupIdPath = baseDir.resolve(groupId.replace('.', '/'));
        if (Files.isDirectory(groupIdPath)) {
            try (Stream<Path> files = Files.walk(groupIdPath)) {
                files.forEach(p -> {
                    BuildinfoEntry bie = relPathToBuildinfoEntry.computeIfAbsent(
                            p.toString(),
                            k -> BuildinfoEntry.of(baseDir.resolve(k)));
                    result.add(bie);
                });
            } catch (IOException e) {
                throw new UncheckedIOException("Could not walk " + groupId, e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private record BuildinfoEntry(Buildinfo buildinfo, Buildspec buildspec) {
        public static BuildinfoEntry of(Path buildinfoPath) {
            Path buildspecPath = buildinfoPath.getParent()
                    .resolve(buildinfoPath.getFileName().toString().replace(".buildinfo", ".buildspec"));
            return new BuildinfoEntry(Buildinfo.of(buildinfoPath), Buildspec.of(buildspecPath));
        }
    }

    private static AtomicInteger threadIndex = new AtomicInteger();

    public static BuildspecRepository cloneOrFetch(
            String remote,
            String branch,
            Path directory) {

        final CompletableFuture<ReproducibleCentralLayout> delegate = new CompletableFuture<>();
        new Thread(() -> {
            try {
                try (Git git = GitUtils.cloneOrFetchAndReset(remote, branch, directory, 1)) {
                }
                delegate.complete(new ReproducibleCentralLayout(directory));
            } catch (Exception e) {
                delegate.completeExceptionally(e);
            }
        }, "LazyBuildspecRepository-" + threadIndex.incrementAndGet()).start();
        return new LazyBuildspecRepository(delegate);
    }

    public static class LazyBuildspecRepository implements BuildspecRepository {

        private final Future<ReproducibleCentralLayout> delegate;

        public LazyBuildspecRepository(Future<ReproducibleCentralLayout> delegate) {
            super();
            this.delegate = delegate;
        }

        ReproducibleCentralLayout awaitRecipeDirectory() {
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

    }
}
