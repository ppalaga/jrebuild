/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import java.nio.file.Path;
import org.l2x6.pom.tuner.model.*;
import picocli.CommandLine;

public class M2Options {
    @CommandLine.Option(names = {
            "--m2-repo" }, description = """
                    Root directory of local Maven repository
                    """, defaultValue = "~/.m2/repository")
    Path m2Repo;

    @CommandLine.Option(names = {
            "--ref-repo-uri" }, description = """
                    URI of the Maven repository where to look for reference artifacts. Defaults to Maven Central.
                    """, defaultValue = "https://repo1.maven.org/maven2")
    String refRepoUri;

}
