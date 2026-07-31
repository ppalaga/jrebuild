/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.os;

import java.util.Locale;

public enum Arch {
    x86,
    amd64,
    arm32,
    arm64;

    public static Arch current() {
        String archName = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        return switch (archName) {
        case "x86" -> x86;
        case "amd64" -> amd64;
        case "x86_64" -> amd64;
        case "aarch64" -> arm64;
        case "arm" -> arm32;
        default -> throw new IllegalArgumentException("Unexpected os.arch " + archName);
        };
    }
}
