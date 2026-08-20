/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import java.nio.file.Path;
import java.time.Instant;
import picocli.CommandLine;

public class PncOptions {

    @CommandLine.Option(names = { "--pnc-base-url" }, description = "The base URL of PNC build service")
    String pncBaseUri;

    @CommandLine.Option(names = { "--pnc-temp" },
            description = "If present, temporary artifacts in PNC will be taken into account; otherwise only permament artifacts will be considered",
            defaultValue = "false", fallbackValue = "true")
    boolean pncIncludeTemporary;

    @CommandLine.Option(names = {
            "--pnc-builds-older-than" }, description = """
                    A timestamp in 2025-12-01T10:15:30Z format determining how fresh the entries in local pnc-cache must be.
                    You should typically set this to the date of the last relevant PNC build you are aware of.
                    If you assume relevant builds still happen, you should use --pnc-builds-older-than=now thus pulling
                    all build data from PNC on each invacation of this command.
                    If not specified, then it is set to first the execution time on the given day.
                    """)
    String rawMaxPncBuildDate;
    private volatile Instant maxPncBuildDate;

    protected Instant maxPncBuildDate(Path cacheDir) {
        Instant result;
        if ((result = maxPncBuildDate) == null) {
            if (rawMaxPncBuildDate == null) {
                result = maxPncBuildDate = ProjectDirOptions.defaultMinRetrievalTime("pnc-builds-older-than", cacheDir);
            } else if ("now".equals(rawMaxPncBuildDate)) {
                result = maxPncBuildDate = Instant.now();
            } else {
                result = maxPncBuildDate = Instant.parse(rawMaxPncBuildDate);
            }
        }
        return result;
    }

}
