/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.scm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.l2x6.jrebuild.domino.scm.recipes.RecipeManager;

public class ScmInfoManager implements RecipeManager<ScmInfo> {

    @Override
    public ScmInfo parse(InputStream file) throws IOException {
        ScmInfo info = MAPPER.readValue(file, ScmInfo.class);
        if (info == null) {
            return new ScmInfo(); //can happen on empty file
        }
        return info;
    }

    @Override
    public void write(ScmInfo data, OutputStream out) throws IOException {
        MAPPER.writeValue(out, data);
    }
}
