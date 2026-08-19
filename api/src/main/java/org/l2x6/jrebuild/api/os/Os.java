/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.os;

import java.util.Locale;

public enum Os {
    LINUX(Shell.BASH, Eol.LF, ""),
    MACOS(Shell.BASH, Eol.LF, ""),
    WINDOWS(Shell.CMD_EXE, Eol.CRLF, ".exe");

    private final Shell defaultShell;
    private final Eol eol;
    private final String executableSuffix;

    private Os(Shell defaultShell, Eol eol, String executableSuffix) {
        this.defaultShell = defaultShell;
        this.eol = eol;
        this.executableSuffix = executableSuffix;
    }

    public static Os getDefault() {
        return LINUX;
    }

    public static Os current() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("linux")) {
            return LINUX;
        } else if (osName.contains("win")) {
            return WINDOWS;
        } else if (osName.contains("mac")) {
            return MACOS;
        }
        throw new IllegalStateException("os.name " + osName + " is neither Linux, Windows or Mac");
    }

    public Shell defaultShell() {
        return defaultShell;
    }

    public Eol eol() {
        return eol;
    }

    public String executableSuffix() {
        return executableSuffix;
    }
}
