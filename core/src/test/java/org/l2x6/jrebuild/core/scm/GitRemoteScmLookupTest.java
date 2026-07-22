/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.lang.Thread.State;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository.AnnotatedScmRepository;
import org.l2x6.jrebuild.core.scm.GitRemoteScmLookup.UrlEntry;

import static org.assertj.core.api.Assertions.assertThat;

public class GitRemoteScmLookupTest {
    private static final String SOURCE = "T";

    @Test
    void loadStore() {
        final Instant minRetrievalTime = Instant.now();
        final Path file = Paths.get("target/GitRemoteScmLookupTest/loadStore-" + UUID.randomUUID() + ".txt");
        assertThat(file).doesNotExist();

        {
            final Map<AnnotatedScmRepository, UrlEntry> map = GitRemoteScmLookup.load(file);
            assertThat(map).isEmpty();
        }

        /* store some non-empty items */
        final Instant outdatedRetrievalTime = minRetrievalTime.minus(2, ChronoUnit.SECONDS);
        final Instant futureRetrievalTime = minRetrievalTime.plus(2, ChronoUnit.SECONDS);
        GitRemoteScmLookup.store(file, UrlEntry.success(scmRepo("empty"), futureRetrievalTime, Collections.emptyMap()));
        final Map<String, String> fooMap = Map.of("k1", "v1", "k2", "v2");
        GitRemoteScmLookup.store(file, UrlEntry.success(scmRepo("foo"), futureRetrievalTime, fooMap));
        final Map<String, String> fooBarMap = Map.of("fb1", "fbv1", "fb2", "fbv2");
        GitRemoteScmLookup.store(file, UrlEntry.success(scmRepo("bar"), minRetrievalTime, fooBarMap));

        final Map<String, String> outdatedMap = Map.of("old", "outdated");
        GitRemoteScmLookup.store(file, UrlEntry.success(scmRepo("outdated"), outdatedRetrievalTime, outdatedMap));

        GitRemoteScmLookup.store(file, UrlEntry.failure(scmRepo("failed-repo"), outdatedRetrievalTime, "Failure message"));

        Runnable check = () -> {
            final Map<AnnotatedScmRepository, UrlEntry> map = GitRemoteScmLookup.load(file);
            assertThat(map).hasSize(5);
            assertThat(map.get(scmRepo("empty")).refs()).isEmpty();
            assertThat(map.get(scmRepo("empty")).retrievalTime()).isEqualTo(futureRetrievalTime);
            assertThat(map.get(scmRepo("foo")).refs()).isEqualTo(fooMap);
            assertThat(map.get(scmRepo("foo")).retrievalTime()).isEqualTo(futureRetrievalTime);
            assertThat(map.get(scmRepo("bar")).refs()).isEqualTo(fooBarMap);
            assertThat(map.get(scmRepo("bar")).retrievalTime()).isEqualTo(minRetrievalTime);

            assertThat(map.get(scmRepo("outdated")).refs()).isEqualTo(outdatedMap);
            assertThat(map.get(scmRepo("outdated")).retrievalTime()).isEqualTo(outdatedRetrievalTime);

            assertThat(map.get(scmRepo("failed-repo")).retrievalTime()).isEqualTo(outdatedRetrievalTime);
            assertThat(map.get(scmRepo("failed-repo")).refs()).isNull();
            assertThat(map.get(scmRepo("failed-repo")).failureMessage()).isEqualTo("Failure message");
        };
        check.run();

        /* Just load and close, the entries should not change */
        {
            GitRemoteScmLookup remoteScm = null;
            try {
                remoteScm = new GitRemoteScmLookup(file, minRetrievalTime);
            } finally {
                if (remoteScm != null) {
                    remoteScm.close();
                }
            }
            while (remoteScm.runner.getState() != State.TERMINATED) {
                /* await termination */
            }
            check.run();
        }

        {
            final Map<String, String> updatedMap = Map.of("new", "updated");
            GitRemoteScmLookup remoteScm = null;
            try {
                remoteScm = new GitRemoteScmLookup(file, minRetrievalTime) {

                    @Override
                    UrlEntry lsRemote(AnnotatedScmRepository url) {
                        return UrlEntry.success(url, futureRetrievalTime, updatedMap);
                    }

                };
                Map<String, String> refs = remoteScm.getRefs(scmRepo("outdated"), Kind.TAG).result();
                Assertions.assertThat(refs).isEqualTo(updatedMap);
            } finally {
                if (remoteScm != null) {
                    remoteScm.close();
                }
            }
            while (remoteScm.runner.getState() != State.TERMINATED) {
                /* await termination */
            }
            final Map<AnnotatedScmRepository, UrlEntry> map = GitRemoteScmLookup.load(file);
            assertThat(map).hasSize(5);
            assertThat(map.get(scmRepo("empty")).refs()).isEmpty();
            assertThat(map.get(scmRepo("empty")).retrievalTime()).isEqualTo(futureRetrievalTime);
            assertThat(map.get(scmRepo("foo")).refs()).isEqualTo(fooMap);
            assertThat(map.get(scmRepo("foo")).retrievalTime()).isEqualTo(futureRetrievalTime);
            assertThat(map.get(scmRepo("bar")).refs()).isEqualTo(fooBarMap);
            assertThat(map.get(scmRepo("bar")).retrievalTime()).isEqualTo(minRetrievalTime);

            assertThat(map.get(scmRepo("outdated")).refs()).isEqualTo(updatedMap);
            assertThat(map.get(scmRepo("outdated")).retrievalTime()).isEqualTo(futureRetrievalTime);

            assertThat(map.get(scmRepo("failed-repo")).retrievalTime()).isEqualTo(outdatedRetrievalTime);
            assertThat(map.get(scmRepo("failed-repo")).refs()).isNull();
            assertThat(map.get(scmRepo("failed-repo")).failureMessage()).isEqualTo("Failure message");

        }
    }

    static AnnotatedScmRepository scmRepo(String uri) {
        return new AnnotatedScmRepository(SOURCE, "git", uri);
    }
}
