/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CommonUtils {
    private CommonUtils() {
    }

    public static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        StringBuffer buf = sw.getBuffer();
        int last = buf.length() - 1;
        while (last >= 0 && Character.isWhitespace(buf.charAt(last))) {
            last--;
        }
        buf.setLength(last + 1);
        String result = buf.toString().replace("\t", "    ");
        return result;
    }
}
