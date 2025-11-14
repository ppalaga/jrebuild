/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.location;

import java.nio.file.Path;
import java.util.Map;
import org.l2x6.jrebuild.domino.scm.recipes.BuildRecipe;

public class BuildInfoResponse {
    final Map<BuildRecipe, Path> data;

    public BuildInfoResponse(Map<BuildRecipe, Path> data) {
        this.data = data;
    }

    public Map<BuildRecipe, Path> getData() {
        return data;
    }
}
