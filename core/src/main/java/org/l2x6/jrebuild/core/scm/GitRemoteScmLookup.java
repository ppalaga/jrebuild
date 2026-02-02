/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository;

public class GitRemoteScmLookup implements RemoteScmLookup, AutoCloseable {
    private static final Logger log = Logger.getLogger(GitRemoteScmLookup.class);

    private final CompletableFuture<Map<ScmRepository, UrlEntry>> urisToTagsToRevisions = new CompletableFuture<>();
    private final Path cacheFile;
    private final Instant minRetrievalTime;

    private final BlockingQueue<Object> tasks = new LinkedBlockingQueue<>();
    final Thread runner;

    public GitRemoteScmLookup(Path cacheFile, Instant minRetrievalTime) {
        super();
        this.cacheFile = cacheFile;
        if (minRetrievalTime.isAfter(Instant.now())) {
            throw new IllegalArgumentException("minRetrievalTime cannot be in the future");
        }
        this.minRetrievalTime = minRetrievalTime;
        tasks.add(new InitTask());
        runner = new Thread(this::processQueue, cacheFile.getFileName().toString());
        runner.start();
    }

    @Override
    public String getRevision(ScmRepository url, Kind kind, String name) {
        return getRefs(url, kind).get(name);
    }

    @Override
    public Map<String, String> getRefs(ScmRepository url, Kind kind) {
        if (kind != Kind.TAG) {
            throw new IllegalArgumentException("Looking up remote refs other than tags is unsupported");
        }
        final int timeout = 10;
        try {
            return urisToTagsToRevisions.get(timeout, TimeUnit.SECONDS)
                    .compute(url, (k, v) -> {
                        if (v == null || v.retrievalTime.isBefore(minRetrievalTime)) {
                            v = lsRemote(k);
                            /* Append the new entry to the local file asynchronously */
                            tasks.add(v);
                        }
                        return v;
                    }).refs;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Could not retrieve urisToTagsToRevisions within " + timeout + " seconds", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Could not retrieve urisToTagsToRevisions within " + timeout + " seconds", e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Could not retrieve urisToTagsToRevisions within " + timeout + " seconds", e);
        }
    }

    UrlEntry lsRemote(ScmRepository url) {
        log.debugf("Loading tag -> SHA1 mappings from %s", url);

        if (url.uri().contains("fuse-components")) {
            throw new RuntimeException(url.toString());
        }
        Map<String, String> tagsToHash;
        final Collection<Ref> tags;
        final Instant retrievalTime = Instant.now();
        try {
            tags = Git.lsRemoteRepository()
                    .setRemote(url.uri()).setTags(true).call();
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to list of tags from " + url, e);
        }
        tagsToHash = new LinkedHashMap<>(tags.size());
        for (var tag : tags) {
            var name = tag.getName().replace("refs/tags/", "");
            tagsToHash.put(name, tag.getPeeledObjectId() == null ? tag.getObjectId().name() : tag.getPeeledObjectId().name());
        }
        log.tracef("Loaded tags from %s: %s", url, tagsToHash);
        if (tagsToHash.isEmpty()) {
            return new UrlEntry(url, retrievalTime, Collections.emptyMap());
        }
        return new UrlEntry(url, retrievalTime, Collections.unmodifiableMap(tagsToHash));
    }

    void processQueue() {
        while (true) {
            try {
                final Collection<Object> tsks = new ArrayList<>();
                final Object task = tasks.take();
                if (task instanceof InitTask) {
                    ((InitTask) task).run();
                    continue;
                }

                tsks.add(task);
                tasks.drainTo(tsks);

                final Optional<Object> closeTask = tsks.stream()
                        .filter(t -> t instanceof CloseTask)
                        .findFirst();

                if (closeTask.isPresent()) {
                    /* Ignore any other tasks */
                    ((CloseTask) closeTask.get()).run();
                    /* Terminate the runner thread */
                    return;
                } else {
                    store(cacheFile, (Collection<UrlEntry>) (Collection<?>) tsks);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Could not run a tast in " + runner.getName(), e);
            } catch (Exception e) {
                throw new RuntimeException("Could not run a tast in " + runner.getName(), e);
            }

        }
    }

    static Map<ScmRepository, UrlEntry> load(Path file) {
        final Map<ScmRepository, UrlEntry> result = new ConcurrentHashMap<>();
        if (Files.isRegularFile(file)) {
            try {
                Iterator<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8).iterator();
                ScmRepository url = null;
                Instant retrievalTime = null;
                Map<String, String> val = null;
                while (lines.hasNext()) {
                    final String line = lines.next();
                    if (line.isEmpty()) {
                        continue;
                    } else if (!line.startsWith(" ")) {
                        if (url != null) {
                            result.put(url, new UrlEntry(url, retrievalTime, val));
                        }
                        String[] entry = line.split(" ");
                        if (entry.length != 3) {
                            throw new IllegalStateException(
                                    "Url line '" + line + "' in " + file + " has " + entry.length + " elements; expected: 3");
                        }
                        int colonPos = entry[1].indexOf(':');

                        url = new ScmRepository(entry[0], entry[1].substring(0, colonPos), entry[1].substring(colonPos + 1));
                        retrievalTime = Instant.parse(entry[2]);
                        val = new LinkedHashMap<>();
                    } else {
                        String[] entry = line.split(" ");
                        if (entry.length != 3) {
                            throw new IllegalStateException(
                                    "Tag line '" + line + "' in " + file + " has " + entry.length + " elements; expected: 3");
                        }
                        val.put(entry[1], entry[2]);
                    }
                }
                if (url != null) {
                    result.put(url, new UrlEntry(url, retrievalTime, val));
                }
            } catch (IOException e) {
                throw new RuntimeException("Could not read " + file, e);
            }
            log.infof("Loaded tag -> SHA mappings for %d git repositories from %s", result.size(), file);
        }
        return result;
    }

    static void store(Path cacheFile, UrlEntry tags) {
        store(cacheFile, Collections.singletonList(tags));
    }

    static void store(Path cacheFile, Collection<UrlEntry> tags) {
        try {
            Files.createDirectories(cacheFile.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Could not create " + cacheFile.getParent(), e);
        }
        try (Writer w = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            for (UrlEntry t : tags) {
                t.append(w);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not write to " + cacheFile, e);
        }
    }

    @Override
    public String type() {
        return "git";
    }

    @Override
    public void close() {
        CloseTask closeTask = new CloseTask();
        tasks.add(closeTask);
        closeTask.await(Duration.ofSeconds(10));
    }

    class CloseTask implements Runnable {

        private final CompletableFuture<Integer> result = new CompletableFuture<>();

        @Override
        public void run() {
            try {
                final Map<ScmRepository, UrlEntry> uris = urisToTagsToRevisions.get(2, TimeUnit.SECONDS);
                final Set<ScmRepository> sortedUris = new TreeSet<>(uris.keySet());
                log.infof("Storing tag -> SHA mappings for %d git repositories to %s due to application shutdown", sortedUris.size(), cacheFile);
                Files.createDirectories(cacheFile.getParent());
                try (Writer w = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8)) {
                    for (ScmRepository uri : sortedUris) {
                        final UrlEntry tags = uris.get(uri);
                        tags.append(w);
                    }
                }
                result.complete(sortedUris.size());
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        }

        public Integer await(Duration timeout) {
            try {
                return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while writing to " + cacheFile, e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed writing to " + cacheFile, e);
            } catch (TimeoutException e) {
                throw new RuntimeException("Timeout while writing to " + cacheFile, e);
            }
        }

    }

    class InitTask implements Runnable {
        @Override
        public void run() {
            try {
                urisToTagsToRevisions.complete(load(cacheFile));
            } catch (Exception e) {
                urisToTagsToRevisions.completeExceptionally(e);
            }
        }
    }

    static record UrlEntry(ScmRepository url, Instant retrievalTime, Map<String, String> refs) {
        public void append(Appendable out) throws IOException {
            out.append(url.toString()).append(' ').append(retrievalTime.toString()).append('\n');
            refs.forEach((kk, vv) -> {
                try {
                    out.append(' ').append(kk).append(' ').append(vv).append('\n');
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            out.append('\n');
        }

    }

}
