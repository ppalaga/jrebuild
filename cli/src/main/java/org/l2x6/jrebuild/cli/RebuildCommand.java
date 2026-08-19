/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import org.jboss.logging.Logger;
import picocli.CommandLine;

@CommandLine.Command(name = "rebuild")
public class RebuildCommand implements Runnable {
    private static final Logger log = Logger.getLogger(RebuildCommand.class);

    public RebuildCommand() {
    }

    @Override
    public void run() {

    }

}
