/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.os;

import org.l2x6.jrebuild.api.os.Tool.InstalledTool;

public interface Packager {
    String name();

    InstalledTool install(Tool tool);
}
