package org.l2x6.jrebuild.core.build;

public enum Os {
    LINUX(Shell.BASH),
    MACOS(Shell.BASH),
    WINDOWS(Shell.CMD_EXE);

    private final Shell defaultShell;

    private Os(Shell defaultShell) {
        this.defaultShell = defaultShell;
    }

    public static Os getDefault() {
        return LINUX;
    }

    public Shell defaultShell() {
        return defaultShell;
    }
}
