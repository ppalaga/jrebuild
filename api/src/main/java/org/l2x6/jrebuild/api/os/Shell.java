/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.os;

import java.util.List;

public enum Shell {
    BASH() {
        public List<String> command(OsArch osArch, String script) {
            return List.of("bash" + osArch.os().executableSuffix(), "-c", script);
        }
    },
    POWER_SHELL() {
        public List<String> command(OsArch osArch, String script) {
            if (!osArch.os().equals(Os.WINDOWS)) {
                throw new IllegalStateException("Cannot run PowerShell on " + osArch.os());
            }
            return List.of("pwsh" + osArch.os().executableSuffix(), "-Command", script);
        }
    },
    CMD_EXE() {
        public List<String> command(OsArch osArch, String script) {
            if (!osArch.os().equals(Os.WINDOWS)) {
                throw new IllegalStateException("Cannot run cmd.exe on " + osArch.os());
            }
            return List.of("cmd" + osArch.os().executableSuffix(), "/c", script);
        }
    };

    /**
     * @param  osArch the {@link OsArch} to run on
     * @param  script the script to run
     * @return        a {@link List} consisting of the shell executable and a list of arguments needed to run the given
     *                script
     */
    public abstract List<String> command(OsArch osArch, String script);
}
