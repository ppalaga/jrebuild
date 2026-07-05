/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.os;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * One or more executables callable from a build script.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public record Tool(
        /** Such as {@code sdkman} */
        String packagerName,
        /** Name of the installable package */
        String name,
        String version,
        /** Used esp. for Java; e.g. {@code temurin} or {@code corretto} */
        String distribution) {

    public String versionDistribution() {
        return distribution == null ? version : (version + "-" + distribution);
    }

    public static record InstalledTool(Tool tool, Collection<String> pathEnvironmentVariableEntries) {
        public void preparePathEnvironmentVariable(Consumer<String> pathEnvironmentVariable) {
            pathEnvironmentVariableEntries.forEach(pathEnvironmentVariable::accept);
        }
    }
}
