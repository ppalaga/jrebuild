/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import java.util.Objects;
import org.l2x6.pom.tuner.model.Gav;

public record FqScmRef(ScmRef scmRef, AnnotatedScmRepository repository, String failureMessage) {

    public FqScmRef(ScmRef scmRef, AnnotatedScmRepository repository) {
        this(scmRef, repository, null);
    }

    public FqScmRef(ScmRef scmRef, AnnotatedScmRepository repository, String failureMessage) {
        this.scmRef = Objects.requireNonNull(scmRef);
        this.repository = Objects.requireNonNull(repository);
        this.failureMessage = failureMessage;
    }

    public static FqScmRef createUnknown(Gav gav) {
        return new FqScmRef(ScmRef.createUnknown(gav.getVersion()), AnnotatedScmRepository.createUnknown(gav));
    }

    public static FqScmRef createFailed(String version, AnnotatedScmRepository repository, String failureMessage) {
        return new FqScmRef(ScmRef.createFailed(version), repository, failureMessage);
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
