/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common;

public class StackTraceLessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StackTraceLessException(String message, Throwable cause) {
        super(message, cause);
    }

    public StackTraceLessException(String message) {
        super(message);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

}
