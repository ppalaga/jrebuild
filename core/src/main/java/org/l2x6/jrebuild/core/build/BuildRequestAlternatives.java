package org.l2x6.jrebuild.core.build;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.l2x6.jrebuild.api.os.Arch;
import org.l2x6.jrebuild.api.os.Os;
import org.l2x6.jrebuild.api.os.Shell;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.scm.FqScmRef;

public record BuildRequestAlternatives(
        BuildGroup<FqScmRef> buildGroup,
        Os os,
        Arch arch,
        Shell shell,
        Set<BuildToolAndVersion> buildTool,
        Set<JavaDistroAndVersion> java,
        String buildScript) {

}
