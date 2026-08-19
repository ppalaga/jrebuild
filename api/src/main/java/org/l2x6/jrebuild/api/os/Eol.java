/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.os;

public enum Eol {
    CR("\r", "\\r"), LF("\n", "\\n"), CRLF("\r\n", "\\r\\n");

    private Eol(String eolString, String escapedEolString) {
        this.eolString = eolString;
        this.escapedEolString = escapedEolString;
        this.eolBytes = new byte[eolString.length()];
        for (int i = 0; i < eolBytes.length; i++) {
            eolBytes[i] = (byte) eolString.charAt(i);
        }
    }

    private final byte[] eolBytes;
    private final String eolString;
    private final String escapedEolString;

    public String eolString() {
        return eolString;
    }

    public String escapedEolString() {
        return escapedEolString;
    }
}
