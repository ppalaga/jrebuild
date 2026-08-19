/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import java.nio.file.Path;
import picocli.CommandLine;

public class ProjectDirOptions extends CacheOptions {

    @CommandLine.Option(names = { "--project-dir" }, description = "A directory containing a source tree to analyze")
    private Path projectDir;
    private volatile Path projectDirResolved;

    protected Path projectDir() {
        Path result;
        if ((result = projectDirResolved) == null) {
            result = projectDirResolved = resolveHome(projectDir);
        }
        return result;
    }

}
