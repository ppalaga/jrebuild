package org.l2x6.jrebuild.core.build;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import org.l2x6.jrebuild.api.os.Arch;
import org.l2x6.jrebuild.api.os.Os;
import org.l2x6.jrebuild.api.os.OsArch;
import org.l2x6.jrebuild.api.os.Shell;
import org.l2x6.jrebuild.api.os.Tool;

public record BuildRequest(
        BuildGroup buildGroup,
        Os os,
        Arch arch,
        Shell shell,
        List<Tool> tools,
        String buildScript) {

    @JsonIgnore
    public OsArch osArch() {
        return new OsArch(os, arch);
    }
}
