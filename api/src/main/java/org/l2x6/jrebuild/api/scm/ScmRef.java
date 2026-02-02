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
        TAG, BRANCH, COMMIT, REVISION_ID, UNKNOWN(true, "❌"), FAILED(true, "💣");

        private Kind() {
            this(false, "✅");
        }

        private Kind(boolean unknownOrFailed, String icon) {
            this.unknownOrFailed = unknownOrFailed;
            this.icon = icon;
        }

        private final boolean unknownOrFailed;
        private final String icon;

        public String icon() {
            return icon;
        }

        public boolean isUnknownOrFailed() {
            return unknownOrFailed;
        }

        public ScmRef createRef(String name, String revision) {
            return new ScmRef(this, name, revision);
        }
    }

    public static ScmRef createUnknown(String version) {
        return new ScmRef(Kind.UNKNOWN, "unknown-for-version-" + version, null);
    }

    public static ScmRef createFailed(String version) {
        return new ScmRef(Kind.FAILED, "failed-for-version-" + version, null);
    }

    public boolean isUnknownOrFailed() {
        return kind.isUnknownOrFailed();
    }

    @Override
    public String toString() {
        return name + "@" + revision;
    }

}
