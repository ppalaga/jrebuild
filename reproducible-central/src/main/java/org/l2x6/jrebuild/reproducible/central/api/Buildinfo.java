/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import org.l2x6.pom.tuner.model.Ga;
import org.l2x6.pom.tuner.model.Gav;

public record Buildinfo(Gav gav, Set<Gav> gavs) {
    public static Buildinfo of(Path file) {
        Properties props = new Properties();

        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + file, e);
        }

        final String version = props.getProperty("version");
        final Gav gav = new Gav(props.getProperty("group-id"), props.getProperty("artifact-id"), version);

        final Set<Gav> gavs = new LinkedHashSet<>();
        for (Entry<Object, Object> en : props.entrySet()) {
            String key = (String) en.getKey();
            if (key.startsWith("outputs.") && key.endsWith(".coordinates")) {
                String val = (String) en.getValue();
                gavs.add(Ga.of(val).toGav(version));
            }
        }
        return new Buildinfo(gav, Collections.unmodifiableSet(gavs));
    }
}
