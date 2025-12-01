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
    String getRevision(String url, ScmRef.Kind kind, String name);

    Map<String, String> getRefs(String url, ScmRef.Kind kind);

    static class MutableRemoteScmLookup implements RemoteScmLookup {

        private final Map<String, Map<String, String>> entries = new LinkedHashMap<>();

        public MutableRemoteScmLookup put(String url, Map<String, String> tags) {
            entries.put(url, tags);
            return this;
        }

        @Override
        public String getRevision(String url, Kind kind, String name) {
            return getRefs(url, kind).get(name);
        }

        @Override
        public Map<String, String> getRefs(String url, Kind kind) {
            return entries.computeIfAbsent(url, k -> Collections.emptyMap());
        }

    }

}
