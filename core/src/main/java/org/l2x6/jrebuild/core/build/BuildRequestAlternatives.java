/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.*;
import java.util.stream.Stream;
import org.l2x6.jrebuild.api.os.Arch;
import org.l2x6.jrebuild.api.os.Os;
import org.l2x6.jrebuild.api.os.Shell;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.scm.FqScmRef;

public record BuildRequestAlternatives(
        BuildGroup<FqScmRef> buildGroup,
        Os os,
        Arch arch,
        @JsonDeserialize(as = TreeSet.class) Set<BuildToolAndVersion> buildTool,
        @JsonDeserialize(as = TreeSet.class) Set<JavaDistroAndVersion> java) implements Iterable<BuildRequest> {

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

    @Override
    public Iterator<BuildRequest> iterator() {
        return new BuildRequestAlternativesIterator(this);
    }

    static class BuildRequestAlternativesIterator implements Iterator<BuildRequest> {
        private final BuildRequestAlternatives alternatives;
        private final Iterator<BuildToolAndVersion> buildToolIt;
        private BuildToolAndVersion buildTool;
        private Iterator<JavaDistroAndVersion> javaIt;

        BuildRequestAlternativesIterator(BuildRequestAlternatives alternatives) {
            this.alternatives = alternatives;
            this.buildToolIt = alternatives.buildTool.iterator();
            this.buildTool = buildToolIt.hasNext() ? buildToolIt.next() : null;
            this.javaIt = alternatives.java.iterator();
        }

        @Override
        public boolean hasNext() {
            return buildTool != null && javaIt.hasNext();
        }

        @Override
        public BuildRequest next() {
            if (!hasNext()) {
                throw new ArrayIndexOutOfBoundsException();
            }
            JavaDistroAndVersion j = javaIt.next();
            List<Tool> tools = Stream.of(buildTool.tool(), j.tool())
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            String buildScript = alternatives.createBuildScript(buildTool.buildTool(), alternatives.os.defaultShell());
            BuildRequest result = new BuildRequest(
                    alternatives.buildGroup,
                    alternatives.os,
                    alternatives.arch,
                    alternatives.os.defaultShell(),
                    tools,
                    buildScript);
            if (!javaIt.hasNext()) {
                this.buildTool = buildToolIt.hasNext() ? buildToolIt.next() : null;
                javaIt = alternatives.java.iterator();
            }
            return result;
        }
    }

}
