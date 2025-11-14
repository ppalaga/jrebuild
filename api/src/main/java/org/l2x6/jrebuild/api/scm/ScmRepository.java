/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

public record ScmRepository(
        String type,
        String uri) {
    public static ScmRepository UNKNOWN = new ScmRepository("git", "<unknown>");

    @Override
    public String toString() {
        return uri;
    }

}
