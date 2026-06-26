package org.l2x6.jrebuild.core.build;

import java.util.List;
import org.l2x6.jrebuild.api.os.OsArch;
import org.l2x6.jrebuild.api.os.Shell;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.scm.FqScmRef;

public record BuildRequest(
        OsArch osArch,
        Shell shell,
        List<Tool> tools,
        String buildScript,
        BuildGroup buildGroup,
        FqScmRef scmRef,
        Reproducibility requiredReproducibility) {

}
