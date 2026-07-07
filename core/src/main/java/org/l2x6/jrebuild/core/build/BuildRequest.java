package org.l2x6.jrebuild.core.build;

import java.util.List;
import org.l2x6.jrebuild.api.os.OsArch;
import org.l2x6.jrebuild.api.os.Shell;
import org.l2x6.jrebuild.api.os.Tool;

public record BuildRequest(
        BuildGroup buildGroup,
        OsArch osArch,
        Shell shell,
        List<Tool> tools,
        String buildScript) {

}
