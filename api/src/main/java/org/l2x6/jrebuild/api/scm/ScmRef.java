/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

public record ScmRef(
        Kind kind,
        String name,
        String revision) {

    public String revisionOrUnknownRevision() {
        if (revision == null) {
            return "unknown-revision";
        }
        return revision;
    }

    public static enum Kind {
        TAG, BRANCH, COMMIT, UNKNOWN("❌"), FAILED("💣");

        private Kind() {
            this("✅");
        }

        private Kind(String icon) {
            this.icon = icon;
        }

        private final String icon;

        public String icon() {
            return icon;
        }

        public boolean isUnknownOrFailed() {
            return this == UNKNOWN || this == FAILED;
        }

        public ScmRef createRef(String name, String revision) {
            return new ScmRef(this, name, revision);
        }

        boolean isUnknown() {
            return this == UNKNOWN;
        }

        boolean isFailed() {
            return this == FAILED;
        }
    }

    public static ScmRef createUnknown(String version) {
        return new ScmRef(Kind.UNKNOWN, "unknown-for-version-" + version, null);
    }

    public static ScmRef createFailed(String version) {
        return new ScmRef(Kind.FAILED, "failed-for-version-" + version, null);
    }

    public boolean isUnknown() {
        return kind.isUnknown();
    }

    public boolean isFailed() {
        return kind.isFailed();
    }

    public boolean isUnknownOrFailed() {
        return kind.isUnknownOrFailed();
    }

    @Override
    public String toString() {
        return name + "@" + revision;
    }

}
