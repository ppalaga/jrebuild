/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.transport.URIish;
import org.l2x6.jrebuild.api.scm.ScmRepository.ScmRepositoryType;

public record ScmRepository(
        ScmRepositoryType type,
        String uri) implements Comparable<ScmRepository> {

    public enum ScmRepositoryType {
        unknown,
        failed,
        git,
        svn,
        cvs
    }

    private static final Comparator<ScmRepository> COMPARATOR = Comparator.comparing(ScmRepository::uri)
            .thenComparing(ScmRepository::type);

    public ScmRepository(
            ScmRepositoryType type,
            String uri) {
        this.type = Objects.requireNonNull(type, "type");
        Objects.requireNonNull(uri, "uri");
        if (!uri.startsWith("file://") && uri.endsWith("/")) {
            throw new IllegalArgumentException("URI must not end with /; found '" + uri + "'");
        }
        this.uri = uri;
    }

    public ScmRepository(String type, String uri) {
        this(ScmRepositoryType.valueOf(Objects.requireNonNull(type, "type")), uri);
    }

    @Override
    public String toString() {
        return type + ":" + uri;
    }

    @Override
    public int compareTo(ScmRepository o) {
        return COMPARATOR.compare(this, o);
    }

    public Optional<String> lastPathSegment() {
        if (uri == null) {
            return Optional.empty();
        }
        if (type == ScmRepositoryType.git) {
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

    public boolean isGit() {
        return type == ScmRepositoryType.git;
    }

}
