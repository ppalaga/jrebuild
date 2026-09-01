/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common.io;

import java.util.regex.Pattern;

public class IoUtils {
    static final String FORBIDDEN_CHARS = "<>:\"/|?*";
    private static final Pattern FORBIDDEN_ON_WINDOWS = Pattern.compile("[" + FORBIDDEN_CHARS + "\\\\]+");
    private static final Pattern TRAILING_SLASHES_PATTERN = Pattern.compile("[/\\\\]+$");

    public IoUtils() {
    }

    public static String sanitizeFileName(String name) {
        return FORBIDDEN_ON_WINDOWS.matcher(name).replaceAll("");
    }

    public static String removeTrailingSlashes(String path) {
        return TRAILING_SLASHES_PATTERN.matcher(path).replaceAll("");
    }
}
