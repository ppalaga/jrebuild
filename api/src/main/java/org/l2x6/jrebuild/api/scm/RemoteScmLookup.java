/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;

public interface RemoteScmLookup {
    String type();

    /**
     * Get the revision ID (sha for git) for the given reference (typically a tag)
     *
     * @param  url  the SCM repository where to look for the given tag
     * @param  kind kinf of reference, typically a {@link Kind#TAG}
     * @param  name name of the reference, typically the tag name
     * @return      a revision ID (sha for git) or {@code null} if the given reference does not exist in the given SCM
     *              repository
     */
    default Result<String, String> getRevision(ScmRepository.AnnotatedScmRepository url, ScmRef.Kind kind, String name) {
        return getRefs(url, kind).mapResult(r -> r.get(name))
                .verify(r -> (r.result() == null) ? Result.failure("No such " + kind + " " + name + " in " + url) : r);
    }

    Result<Map<String, String>, String> getRefs(ScmRepository.AnnotatedScmRepository url, ScmRef.Kind kind);

    static class AggregateRemoteScmLookup implements RemoteScmLookup, AutoCloseable {

        private final Map<String, RemoteScmLookup> lookups;

        public AggregateRemoteScmLookup(RemoteScmLookup... lookups) {
            super();
            Map<String, RemoteScmLookup> m = new LinkedHashMap<>();
            for (RemoteScmLookup l : lookups) {
                m.put(l.type(), l);
            }
            this.lookups = Map.copyOf(m);
        }

        @Override
        public void close() {
            for (RemoteScmLookup l : lookups.values()) {
                if (l instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) l).close();
                    } catch (Exception e) {
                    }
                }
            }
        }

        @Override
        public Result<Map<String, String>, String> getRefs(ScmRepository.AnnotatedScmRepository url, Kind kind) {
            RemoteScmLookup result = lookups.get(url.type());
            if (result == null) {
                return Result.failure("SCM type '" + url.type() + "' unsupported: " + url);
            }
            return result.getRefs(url, kind);
        }

        @Override
        public String type() {
            throw new UnsupportedOperationException();
        }

    }

    static class MutableRemoteScmLookup implements RemoteScmLookup {

        private final Map<ScmRepository.AnnotatedScmRepository, Result<Map<String, String>, String>> entries = new LinkedHashMap<>();

        private final String type;

        public MutableRemoteScmLookup(String type) {
            super();
            this.type = type;
        }

        public MutableRemoteScmLookup put(ScmRepository.AnnotatedScmRepository url, Result<Map<String, String>, String> tags) {
            entries.put(url, tags);
            return this;
        }

        @Override
        public Result<Map<String, String>, String> getRefs(ScmRepository.AnnotatedScmRepository url, Kind kind) {
            return entries.computeIfAbsent(url, k -> Result.success(Collections.emptyMap()));
        }

        @Override
        public String type() {
            return type;
        }

    }

}
