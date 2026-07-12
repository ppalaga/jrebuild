package org.l2x6.jrebuild.core.build.service;

import java.nio.file.Path;
import org.l2x6.jrebuild.core.build.CompoundResourceMatch;

public interface CompoundResourceMatchService {

    public CompoundResourceMatch assessMatch(Path rebuiltArtifact, Path referenceArtifact);

}
