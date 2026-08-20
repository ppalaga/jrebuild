/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import java.util.List;
import java.util.Map;

public record CompoundResourceMatch(
        ResourceMatchLevel resourceMatch,
        Map<ResourceMatchLevel, List<String>> componentMatches) {
}
