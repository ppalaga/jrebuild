/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.mutiny;

import io.vertx.core.file.OpenOptions;

public class MutinyConstants {
    public static final OpenOptions READ_OPTIONS = new OpenOptions().setRead(true).setWrite(false).setCreate(false);
    public static final OpenOptions WRITE_CREATE_OPTIONS = new OpenOptions();

    private MutinyConstants() {

    }
}
