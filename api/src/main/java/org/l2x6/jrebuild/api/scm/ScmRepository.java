/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import org.eclipse.jgit.transport.URIish;
import org.l2x6.jrebuild.api.util.Ebnfizer;
import org.l2x6.pom.tuner.model.Gav;

public record ScmRepository(
        String source,
        String type,
        String uri) implements Comparable<ScmRepository> {
    public static String UNKNOWN = "unknown";
    public static String FAILED = "failed";
    private static final Comparator<ScmRepository> COMPARATOR = Comparator.comparing(ScmRepository::uri)
            .thenComparing(ScmRepository::type).thenComparing(ScmRepository::source);

    public static ScmRepository of(String asString) {
        String[] parts = asString.split(" ");
        if (parts.length != 3) {
            throw new IllegalStateException("Two spaces expected in '" + asString + "'");
        }
        return new ScmRepository(parts[0], parts[1], parts[2]);
    }

    public static ScmRepository createUnknown(Gav gav) {
        return new ScmRepository("?", UNKNOWN, gav.getGroupId());
    }

    public static ScmRepository createFailed(Collection<ScmRepository> failedRepositories) {

        return new ScmRepository(
                "?",
                FAILED,
                new Ebnfizer().add(failedRepositories.stream().map(ScmRepository::toString)).toString());
    }

    public boolean isUnknown() {
        return UNKNOWN.equals(type);
    }

    public boolean isFailed() {
        return FAILED.equals(type);
    }

    public boolean isKnown() {
        return !UNKNOWN.equals(type);
    }

    @Override
    public String toString() {
        return source + " " + type + ":" + uri;
    }

    @Override
    public int compareTo(ScmRepository o) {
        return COMPARATOR.compare(this, o);
    }

    public boolean isUnknownOrFailed() {
        return UNKNOWN.equals(type) || FAILED.equals(type);
    }

    public Optional<String> lastPathSegment() {
        if (uri == null) {
            return Optional.empty();
        }
        if ("git".equals(type)) {
            try {
                URIish urish = new URIish(uri);
                String p = urish.getPath();
                if (p != null) {
                    if (p.endsWith(".git")) {
                        p = p.substring(0, p.length() - 4);
                    }
                    int slashPos = p.lastIndexOf('/');
                    return Optional.of(slashPos >= 0 ? p.substring(slashPos + 1) : p);
                }
            } catch (URISyntaxException ignored) {
            }
        }
        final String p = URI.create(uri).getPath();
        if (p != null) {
            int slashPos = p.lastIndexOf('/');
            return Optional.of(slashPos >= 0 ? p.substring(slashPos + 1) : p);
        }
        return Optional.empty();
    }

}
