/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;

public class GitRemoteScmLookup implements RemoteScmLookup, AutoCloseable {
    private static final Logger log = Logger.getLogger(GitRemoteScmLookup.class);

    private final Map<String, UrlEntry> urisToTagsToRevisions;
    private final Path cacheFile;
    private final Object cacheFileLock = new Object();

    static Map<String, UrlEntry> load(Path file, Object tagInfoFileLock, Instant minRetrievalTime) {
        final Map<String, UrlEntry> result = new ConcurrentHashMap<>();
        if (Files.isRegularFile(file)) {
            synchronized (tagInfoFileLock) {
                try {
                    Iterator<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8).iterator();
                    String url = null;
                    Instant retrievalTime = null;
                    Map<String, String> val = null;
                    while (lines.hasNext()) {
                        final String line = lines.next();
                        if (line.isEmpty()) {
                            continue;
                        } else if (!line.startsWith(" ")) {
                            if (url != null) {
                                if (!minRetrievalTime.isAfter(minRetrievalTime)) {
                                    result.put(url, new UrlEntry(retrievalTime, val));
                                }
                            }
                            String[] entry = line.split(" ");
                            if (entry.length != 2) {
                                throw new IllegalStateException("Url line '" + line + "' has " + entry.length + " elements");
                            }
                            url = entry[0];
                            retrievalTime = Instant.parse(entry[1]);
                            val = new LinkedHashMap<>();
                        } else {
                            String[] entry = line.split(" ");
                            if (entry.length != 3) {
                                throw new IllegalStateException("Tag line '" + line + "' has " + entry.length + " elements");
                            }
                            val.put(entry[1], entry[2]);
                        }
                    }
                    if (url != null) {
                        result.put(url, new UrlEntry(retrievalTime, val));
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Could not read " + file, e);
                }
            }
            log.infof("Loaded tag -> SHA mappings for %d git repositories from %s", result.size(), file);
        }
        return result;
    }

    public GitRemoteScmLookup(Path cacheFile, Instant minRetrievalTime) {
        super();
        this.cacheFile = cacheFile;
        this.urisToTagsToRevisions = load(cacheFile, cacheFileLock, minRetrievalTime);
    }

    @Override
    public String getRevision(String url, Kind kind, String name) {
        return getRefs(url, kind).computeIfAbsent(name, null);
    }

    @Override
    public Map<String, String> getRefs(String url, Kind kind) {
        if (kind != Kind.TAG) {
            throw new IllegalArgumentException("Looking up remote refs other than tags is unsupported");
        }
        return urisToTagsToRevisions.computeIfAbsent(url, k -> {
            final UrlEntry tags = getTagToHashMapFromGit(k);
            /* Append the new entry to the local file */
            store(cacheFile, cacheFileLock, k, tags);
            return tags;
        }).refs;
    }

    private static UrlEntry getTagToHashMapFromGit(String url) {
        Map<String, String> tagsToHash;
        final Collection<Ref> tags;
        final Instant retrievalTime = Instant.now();
        try {
            tags = Git.lsRemoteRepository()
                    .setRemote(url).setTags(true).call();
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to list of tags from " + url, e);
        }
        tagsToHash = new LinkedHashMap<>(tags.size());
        for (var tag : tags) {
            var name = tag.getName().replace("refs/tags/", "");
            tagsToHash.put(name, tag.getPeeledObjectId() == null ? tag.getObjectId().name() : tag.getPeeledObjectId().name());
        }
        if (tagsToHash.isEmpty()) {
            return new UrlEntry(retrievalTime, Collections.emptyMap());
        }
        return new UrlEntry(retrievalTime, Collections.unmodifiableMap(tagsToHash));
    }

    static void store(Path cacheFile, Object cacheFileLock, String url, UrlEntry tags) {
        final byte[] bytes = tags.toString(url).getBytes(StandardCharsets.UTF_8);
        synchronized (cacheFileLock) {
            try {
                Files.createDirectories(cacheFile.getParent());
                Files.write(cacheFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new RuntimeException("Could not write to " + cacheFile, e);
            }
        }
    }

    @Override
    public void close() throws Exception {
        Set<String> sortedUris = new TreeSet<>(urisToTagsToRevisions.keySet());
        synchronized (cacheFileLock) {
            try {
                Files.createDirectories(cacheFile.getParent());
                try (Writer w = Files.newBufferedWriter(cacheFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    for (String uri : sortedUris) {
                        final UrlEntry tags = urisToTagsToRevisions.get(uri);
                        w.write(tags.toString(uri));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Could not write to " + cacheFile, e);
            }
        }

    }

    static record UrlEntry(Instant retrievalTime, Map<String, String> refs) {
        public String toString(String url) {
            final StringBuilder sb = new StringBuilder(url).append(' ').append(retrievalTime.toString()).append('\n');
            refs.forEach((kk, vv) -> sb.append(' ').append(kk).append(' ').append(vv).append('\n'));
            sb.append('\n');
            return sb.toString();
        }
    }

}
