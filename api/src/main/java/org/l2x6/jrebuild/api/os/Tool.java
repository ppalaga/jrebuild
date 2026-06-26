/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.os;

import java.nio.file.Path;

public record Tool(
        /** Such as {@code sdkman} */
        String packagerName,
        /** Name of the installable package */
        String name,
        /** The command name, such as {@code mvn}, {@code java} without directory. Should be installed in PATH */
        String executable,
        String version,
        /** Used esp. for Java; e.g. {@code temurin} or {@code corretto} */
        String distribution) {

    public String versionDistribution() {
        return distribution == null ? version : (version + "-" + distribution);
    }

    public static record InstalledTool(Tool tool, Path executable) {
    }
}
