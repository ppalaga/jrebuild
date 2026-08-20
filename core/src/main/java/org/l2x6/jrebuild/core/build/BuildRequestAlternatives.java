/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import java.util.LinkedHashSet;
import java.util.Set;
import org.l2x6.jrebuild.api.os.Arch;
import org.l2x6.jrebuild.api.os.Os;
import org.l2x6.jrebuild.api.os.Shell;
import org.l2x6.jrebuild.api.scm.FqScmRef;

public record BuildRequestAlternatives(
        BuildGroup<FqScmRef> buildGroup,
        Os os,
        Arch arch,
        Set<BuildToolAndVersion> buildTool,
        Set<JavaDistroAndVersion> java) {

    public String createBuildScript(BuildTool bt, Shell shell) {

        StringBuilder cmd = new StringBuilder(bt.command());
        if (os == Os.WINDOWS && ("mvn".equals(cmd.toString()) || "./mvnw".equals(cmd.toString()))) {
            cmd.append(".cmd");
        }
        cmd.append(
                " clean deploy -ntp -DskipTests -Dgpg.skip -DskipPublishing -Dcheckstyle.skip -Dpmd.skip deploy:deploy -DaltDeploymentRepository=local::${DEPLOYMENT_REPO}");

        Set<String> releaseProfiles = new LinkedHashSet<>();
        if (buildGroup.hasJavaDoc()) {
            // TODO: figure out whether there is some special profile for javadoc and sources
        }
        if (buildGroup.hasSources()) {
            // TODO: figure out whether there is some special profile for javadoc and sources
        }
        releaseProfiles.forEach(profile -> cmd.append(" -P").append(profile));

        return cmd.toString();
    }

}
