/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import java.util.Objects;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository.ScmRepositoryType;
import org.l2x6.pom.tuner.model.Gav;

public interface FqScmRef {

    static FqScmRef of(ScmRef scmRef, ScmRepository repository) {
        return new FqScmRefRecord(scmRef, repository);
    }

    static FqScmRef of(String rawScmRef) {
        ScmRepositoryType type = ScmRepositoryType.git;
        for (ScmRepositoryType t : ScmRepositoryType.values()) {
            if (rawScmRef.startsWith(t.name() + ":")) {
                type = t;
                rawScmRef = rawScmRef.substring(t.name().length() + 1);
                break;
            }
        }
        int hashPos = rawScmRef.lastIndexOf('#');
        if (hashPos < 0) {
            throw new IllegalArgumentException(FqScmRef.class.getSimpleName() + " must end with #<tag>; found " + rawScmRef);
        }
        final String tag = rawScmRef.substring(hashPos + 1);
        final String uri = rawScmRef.substring(0, hashPos);
        return FqScmRef.of(new ScmRef(Kind.TAG, tag, null), ScmRepository.of(type, uri));
    }

    ScmRef scmRef();

    ScmRepository repository();

    record FqScmRefRecord(
            ScmRef scmRef,
            ScmRepository repository) implements FqScmRef {

        public FqScmRefRecord(ScmRef scmRef, ScmRepository repository) {
            this.scmRef = Objects.requireNonNull(scmRef);
            this.repository = Objects.requireNonNull(repository);
        }

        @Override
        public String toString() {
            return scmRef.kind().icon() + repository + "#" + scmRef;
        }
    }

    record AnnotatedFqScmRef(ScmRef scmRef, ScmRepository.AnnotatedScmRepository repository,
            String failureMessage) implements FqScmRef {

        public AnnotatedFqScmRef(ScmRef scmRef, ScmRepository.AnnotatedScmRepository repository) {
            this(scmRef, repository, null);
        }

        public AnnotatedFqScmRef(ScmRef scmRef, ScmRepository.AnnotatedScmRepository repository, String failureMessage) {
            this.scmRef = Objects.requireNonNull(scmRef);
            this.repository = Objects.requireNonNull(repository);
            this.failureMessage = failureMessage;
        }

        public static FqScmRef.AnnotatedFqScmRef createUnknown(Gav gav) {
            return new FqScmRef.AnnotatedFqScmRef(ScmRef.createUnknown(gav.getVersion()),
                    ScmRepository.AnnotatedScmRepository.createUnknown(gav));
        }

        public static FqScmRef.AnnotatedFqScmRef createFailed(String version, ScmRepository.AnnotatedScmRepository repository,
                String failureMessage) {
            return new FqScmRef.AnnotatedFqScmRef(ScmRef.createFailed(version), repository, failureMessage);
        }

        public boolean isUnknownOrFailed() {
            return scmRef.isUnknownOrFailed() || repository.isUnknownOrFailed();
        }

        @Override
        public String toString() {
            return scmRef.kind().icon() + repository + "#" + scmRef + (failureMessage != null ? (": " + failureMessage) : "");
        }

        public boolean isUnknown() {
            return scmRef.isUnknown() || repository.isUnknown();
        }

        public boolean isFailed() {
            return scmRef.isFailed() || repository.isFailed();
        }

    }

}
