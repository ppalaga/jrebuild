/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build.service;

import java.nio.file.Path;
import org.l2x6.jrebuild.core.build.CompoundResourceMatch;

public interface CompoundResourceMatchService {

    public CompoundResourceMatch assessMatch(Path rebuiltArtifact, Path referenceArtifact);

}
