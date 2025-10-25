/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

public record ScmRef(
        String name,
        Kind kind) {
    public static enum Kind {
        TAG, BRANCH, COMMIT
    }
}
