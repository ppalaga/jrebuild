/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import java.nio.file.Path;
import picocli.CommandLine;

@CommandLine.Command
public class DependencyTreeCommand implements Runnable {

    @CommandLine.Option(names = { "-r",
            "--request" }, description = "org.l2x6.jrebuild.core.api.jrebuildRequest ij JSON or YAML format")
    Path requestPath;

    public DependencyTreeCommand() {
    }

    @Override
    public void run() {
        //
        //        ObjectMapper mapper = new ObjectMapper();
        //        jrebuildRequest request = mapper.readValue(Files.readString(requestPath, StandardCharsets.UTF_8), jrebuildRequest.class);
        //
        //        Context context;
        //        Collection<Gavtcs> rootArtifacts = ManagedGavsSelector.select(context, null, null)
        //        jrebuildRequest request = new jrebuildRequest(null, null, null, null, null);
        //
        //        new DependencyCollector().collect(context, request, null);;

    }
}
