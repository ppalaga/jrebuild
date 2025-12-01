/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.scm.GitRemoteScmLookup.UrlEntry;

import static org.assertj.core.api.Assertions.assertThat;

public class GitRemoteScmLookupTest {

    @Test
    void loadStore() {
        final Object lock = new Object();
        final Instant minRetrievalTime = Instant.now();
        final Path file = Paths.get("target/GitRemoteScmLookupTest/loadStore-" + UUID.randomUUID() + ".txt");
        assertThat(file).doesNotExist();

        {
            final Map<String, UrlEntry> map = GitRemoteScmLookup.load(file, lock, minRetrievalTime);
            assertThat(map).isEmpty();
        }

        /* store some non-empty items */
        final Instant retrievalTime = minRetrievalTime.plus(2, ChronoUnit.SECONDS);
        GitRemoteScmLookup.store(file, lock, "empty", new UrlEntry(retrievalTime, Collections.emptyMap()));
        final Map<String, String> fooMap = Map.of("k1", "v1", "k2", "v2");
        GitRemoteScmLookup.store(file, lock, "foo", new UrlEntry(retrievalTime, fooMap));
        final Map<String, String> fooBarMap = Map.of("fb1", "fbv1", "fb2", "fbv2");
        GitRemoteScmLookup.store(file, lock, "bar", new UrlEntry(retrievalTime, fooBarMap));

        {
            final Map<String, UrlEntry> map = GitRemoteScmLookup.load(file, lock, minRetrievalTime);
            assertThat(map).hasSize(3);
            assertThat(map.get("empty").refs()).isEmpty();
            assertThat(map.get("empty").retrievalTime()).isEqualTo(retrievalTime);
            assertThat(map.get("foo").refs()).isEqualTo(fooMap);
            assertThat(map.get("foo").retrievalTime()).isEqualTo(retrievalTime);
            assertThat(map.get("bar").refs()).isEqualTo(fooBarMap);
            assertThat(map.get("bar").retrievalTime()).isEqualTo(retrievalTime);
        }

    }

}
