/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

public record ScmRef(
        String name,
        Kind kind,
        ScmRepository repository) {
    public static enum Kind {
        TAG, BRANCH, COMMIT, UNKNOWN
    }

    public static ScmRef of(String tag, ScmRepository repository) {
        if (tag == null) {
            return new ScmRef(null, Kind.UNKNOWN, repository);
        } else if ("HEAD".equals(tag)) {
            return new ScmRef("HEAD", Kind.UNKNOWN, repository);
        }
        return new ScmRef(tag, Kind.TAG, repository);
    }

    @Override
    public String toString() {
        return repository + "#" + name;
    }

}
