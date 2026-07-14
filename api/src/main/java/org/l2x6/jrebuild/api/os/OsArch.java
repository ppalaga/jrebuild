/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.os;

public record OsArch(Os os, Arch arch) {
    private static OsArch current;

    public static OsArch current() {
        OsArch result = current;
        if (result == null) {
            result = current = new OsArch(Os.current(), Arch.current());
        }
        return result;
    }

    @Override
    public String toString() {
        return os + "@" + arch;
    }

    public boolean equals(Os os, Arch arch) {
        return this.os.equals(os) && this.arch.equals(arch);
    }

}
