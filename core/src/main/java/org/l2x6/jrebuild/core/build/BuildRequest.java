package org.l2x6.jrebuild.core.build;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Objects;
import org.l2x6.jrebuild.api.os.Arch;
import org.l2x6.jrebuild.api.os.Os;
import org.l2x6.jrebuild.api.os.OsArch;
import org.l2x6.jrebuild.api.os.Shell;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.scm.FqScmRef;

public record BuildRequest(
        BuildGroup<FqScmRef> buildGroup,
        Os os,
        Arch arch,
        Shell shell,
        List<Tool> tools,
        String buildScript) {

    public BuildRequest(BuildGroup<FqScmRef> buildGroup,
            Os os,
            Arch arch,
            Shell shell,
            List<Tool> tools,
            String buildScript) {
        this.buildGroup = Objects.requireNonNull(buildGroup, "buildGroup");
        this.os = Objects.requireNonNull(os, "os");
        this.arch = Objects.requireNonNull(arch, "arch");
        this.shell = Objects.requireNonNull(shell, "shell");
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.buildScript = Objects.requireNonNull(buildScript, "buildScript");
    }

    @JsonIgnore
    public OsArch osArch() {
        return new OsArch(os, arch);
    }
}
