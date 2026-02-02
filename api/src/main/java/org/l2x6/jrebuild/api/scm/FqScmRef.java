/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import java.util.Objects;
import org.l2x6.pom.tuner.model.Gav;

public record FqScmRef(ScmRef scmRef, ScmRepository repository, String failureMessage) {

    public FqScmRef(ScmRef scmRef, ScmRepository repository) {
        this(scmRef, repository, null);
    }

    public FqScmRef(ScmRef scmRef, ScmRepository repository, String failureMessage) {
        this.scmRef = Objects.requireNonNull(scmRef);
        this.repository = Objects.requireNonNull(repository);
        this.failureMessage = failureMessage;
    }

    public static FqScmRef createUnknown(Gav gav) {
        return new FqScmRef(ScmRef.createUnknown(gav.getVersion()), ScmRepository.createUnknown(gav));
    }

    public static FqScmRef createFailed(Gav gav, String failureMessage) {
        return new FqScmRef(ScmRef.createFailed(gav.getVersion()), ScmRepository.createUnknown(gav), failureMessage);
    }

    public boolean isUnknownOrFailed() {
        return scmRef.isUnknownOrFailed() || repository.isUnknown();
    }

    @Override
    public String toString() {
        return scmRef.kind().icon() + repository + "#" + scmRef;
    }

}
