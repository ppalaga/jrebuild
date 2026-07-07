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
        /** A version string the given packager understands */
        String version) {

    public static record InstalledTool(Tool tool, Collection<String> pathEnvironmentVariableEntries) {
        public void preparePathEnvironmentVariable(Consumer<String> pathEnvironmentVariable) {
            pathEnvironmentVariableEntries.forEach(pathEnvironmentVariable::accept);
        }
    }
}
