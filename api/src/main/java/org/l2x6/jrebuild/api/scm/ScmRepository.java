/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.transport.URIish;
import org.l2x6.jrebuild.api.util.Ebnfizer;
import org.l2x6.pom.tuner.model.Gav;

public interface ScmRepository {

    enum ScmRepositoryType {
        unknown,
        failed,
        git,
        svn,
        cvs
    }

    static ScmRepository git(String gitUri) {
        return new ScmRepositoryRecord(ScmRepositoryType.git, gitUri);
    }

    static ScmRepository of(ScmRepositoryType type, String gitUri) {
        return new ScmRepositoryRecord(type, gitUri);
    }

    ScmRepositoryType type();

    String uri();

    @JsonIgnore
    default boolean isGit() {
        return type() == ScmRepositoryType.git;
    }

    default Optional<String> lastPathSegment() {
        String uri = uri();
        if (uri == null) {
            return Optional.empty();
        }
        ScmRepositoryType type = type();
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

    record ScmRepositoryRecord(
            ScmRepositoryType type,
            String uri) implements ScmRepository, Comparable<ScmRepository> {

        private static final Comparator<ScmRepository> COMPARATOR = Comparator.comparing(ScmRepository::uri)
                .thenComparing(ScmRepository::type);

        public ScmRepositoryRecord(
                ScmRepositoryType type,
                String uri) {
            this.type = Objects.requireNonNull(type, "type");
            Objects.requireNonNull(uri, "uri");
            if (!uri.startsWith("file://") && uri.endsWith("/")) {
                throw new IllegalArgumentException("URI must not end with /; found '" + uri + "'");
            }
            this.uri = uri;
        }

        public ScmRepositoryRecord(String type, String uri) {
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

    }

    record AnnotatedScmRepository(
            String source,
            ScmRepository repository) implements Comparable<ScmRepository.AnnotatedScmRepository>, ScmRepository {
        public static String UNKNOWN = "unknown";
        public static String FAILED = "failed";
        private static final Comparator<ScmRepository.AnnotatedScmRepository> COMPARATOR = Comparator
                .comparing(ScmRepository.AnnotatedScmRepository::uri)
                .thenComparing(ScmRepository.AnnotatedScmRepository::type)
                .thenComparing(ScmRepository.AnnotatedScmRepository::source);

        public static ScmRepository.AnnotatedScmRepository of(String asString) {
            String[] parts = asString.split(" ");
            if (parts.length != 3) {
                throw new IllegalStateException("Two spaces expected in '" + asString + "'");
            }
            return new ScmRepository.AnnotatedScmRepository(parts[0], parts[1], parts[2]);
        }

        public static ScmRepository.AnnotatedScmRepository createUnknown(Gav gav) {
            return new ScmRepository.AnnotatedScmRepository("?", UNKNOWN, gav.getGroupId());
        }

        public static ScmRepository.AnnotatedScmRepository createFailed(
                Collection<ScmRepository.AnnotatedScmRepository> failedRepositories) {

            return new ScmRepository.AnnotatedScmRepository(
                    "?",
                    FAILED,
                    new Ebnfizer().add(failedRepositories.stream().map(ScmRepository.AnnotatedScmRepository::toString))
                            .toString());
        }

        public AnnotatedScmRepository(
                String source,
                String type,
                String uri) {
            this(Objects.requireNonNull(source, "source"), new ScmRepositoryRecord(type, uri));
        }

        @JsonIgnore
        public boolean isUnknown() {
            return ScmRepositoryType.unknown == repository.type();
        }

        @JsonIgnore
        public boolean isFailed() {
            return ScmRepositoryType.failed == repository.type();
        }

        @JsonIgnore
        public boolean isKnown() {
            return ScmRepositoryType.unknown != repository.type();
        }

        @Override
        public String toString() {
            return source + " " + repository.toString();
        }

        @Override
        public int compareTo(ScmRepository.AnnotatedScmRepository o) {
            return COMPARATOR.compare(this, o);
        }

        @JsonIgnore
        public boolean isUnknownOrFailed() {
            ScmRepositoryType type = repository.type();
            return ScmRepositoryType.unknown == type || ScmRepositoryType.failed == repository.type();
        }

        public Optional<String> lastPathSegment() {
            return repository.lastPathSegment();
        }

        public String uri() {
            return repository.uri();
        }

        public ScmRepositoryType type() {
            return repository.type();
        }

        public boolean isGit() {
            return repository.isGit();
        }

    }

}
