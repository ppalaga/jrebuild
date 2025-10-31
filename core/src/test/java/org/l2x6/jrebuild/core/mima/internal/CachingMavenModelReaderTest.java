/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.mima.internal;

import eu.maveniverse.maven.mima.context.Context;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.maven.model.DependencyManagement;
import org.assertj.core.api.Assertions;
import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositorySystemSession;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.dep.JrebuildTestUtils;
import org.l2x6.jrebuild.core.mima.JRebuildRuntime;
import org.l2x6.pom.tuner.model.Gav;

public class CachingMavenModelReaderTest {
    private static final Logger log = Logger.getLogger(CachingMavenModelReaderTest.class);

    @Test
    void readModelWithParent() {
        JRebuildRuntime runtime = org.l2x6.jrebuild.core.mima.JRebuildRuntime.getInstance();
        eu.maveniverse.maven.mima.context.ContextOverrides.Builder overrides = JrebuildTestUtils.testRepo();
        final ObservableRepositoryCache cache = new ObservableRepositoryCache();
        try (Context context = runtime.create(
                overrides.build(),
                session -> {
                    session.setCache(cache);
                })) {
            CachingMavenModelReader r = new CachingMavenModelReader(context);

            Assertions.assertThat(cache.cache).isEmpty();
            r.readEffectiveModel(Gav.of("org.l2x6.jrebuild.test-project:jrebuild-test-impl:0.0.1"));
            final List<String> cachedValues = cache.cache.values().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            /* It is caching only parents :( */
            Assertions.assertThat(cachedValues)
                    .containsExactly("org.l2x6.jrebuild.test-project:jrebuild-test-project:pom:0.0.1");
            context.repositorySystemSession().getCache();

        }
    }

    @Test
    void readModelBomWithImport() {
        JRebuildRuntime runtime = org.l2x6.jrebuild.core.mima.JRebuildRuntime.getInstance();
        eu.maveniverse.maven.mima.context.ContextOverrides.Builder overrides = JrebuildTestUtils.testRepo();
        final ObservableRepositoryCache cache = new ObservableRepositoryCache();
        try (Context context = runtime.create(
                overrides.build(),
                session -> {
                    session.setCache(cache);
                })) {
            CachingMavenModelReader r = new CachingMavenModelReader(context);

            Assertions.assertThat(cache.cache).isEmpty();
            r.readEffectiveModel(Gav.of("org.l2x6.jrebuild.test-project:jrebuild-test-bom:0.0.1"));
            final List<String> cachedValues = cache.cache.values().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            Assertions.assertThat(cachedValues).contains("org.l2x6.jrebuild.test-project:jrebuild-test-project:pom:0.0.1");
            /* This is how the imported BOM is cached */
            Assertions.assertThat(cache.cache.values())
                    .satisfies(c -> c.stream().anyMatch(e -> (e instanceof DependencyManagement)));
            Assertions.assertThat(cache.cache).hasSize(2);
        }
    }

    static class ObservableRepositoryCache implements RepositoryCache {

        final Map<Object, Object> cache = new ConcurrentHashMap<>(256);

        public Object get(RepositorySystemSession session, Object key) {
            return cache.get(key);
        }

        public void put(RepositorySystemSession session, Object key, Object data) {
            if (data != null) {
                cache.put(key, data);
            } else {
                cache.remove(key);
            }
        }
    }

}
