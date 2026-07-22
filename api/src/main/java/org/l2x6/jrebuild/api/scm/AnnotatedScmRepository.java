/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import org.l2x6.jrebuild.api.scm.ScmRepository.ScmRepositoryType;
import org.l2x6.jrebuild.api.util.Ebnfizer;
import org.l2x6.pom.tuner.model.Gav;

public record AnnotatedScmRepository(
        String source,
        ScmRepository repository) implements Comparable<AnnotatedScmRepository> {
    public static String UNKNOWN = "unknown";
    public static String FAILED = "failed";
    private static final Comparator<AnnotatedScmRepository> COMPARATOR = Comparator.comparing(AnnotatedScmRepository::uri)
            .thenComparing(AnnotatedScmRepository::type).thenComparing(AnnotatedScmRepository::source);

    public static AnnotatedScmRepository of(String asString) {
        String[] parts = asString.split(" ");
        if (parts.length != 3) {
            throw new IllegalStateException("Two spaces expected in '" + asString + "'");
        }
        return new AnnotatedScmRepository(parts[0], parts[1], parts[2]);
    }

    public static AnnotatedScmRepository createUnknown(Gav gav) {
        return new AnnotatedScmRepository("?", UNKNOWN, gav.getGroupId());
    }

    public static AnnotatedScmRepository createFailed(Collection<AnnotatedScmRepository> failedRepositories) {

        return new AnnotatedScmRepository(
                "?",
                FAILED,
                new Ebnfizer().add(failedRepositories.stream().map(AnnotatedScmRepository::toString)).toString());
    }

    public AnnotatedScmRepository(
            String source,
            String type,
            String uri) {
        this(Objects.requireNonNull(source, "source"), new ScmRepository(type, uri));
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
    public int compareTo(AnnotatedScmRepository o) {
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
