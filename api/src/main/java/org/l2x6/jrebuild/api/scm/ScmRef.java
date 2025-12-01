/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

public record ScmRef(
        Kind kind,
        String name,
        String revision) {

    public static enum Kind {
        TAG(false), BRANCH(false), COMMIT(false), UNKNOWN(true);

        private Kind(boolean unknown) {
            this.unknown = unknown;
        }

        private final boolean unknown;

        public boolean isUnknown() {
            return unknown;
        }
    }

    public static ScmRef createUnknown(String version) {
        return new ScmRef(Kind.UNKNOWN, "unknown-for-version-" + version, null);
    }

    @Override
    public String toString() {
        return name + "@" + revision;
    }

}
