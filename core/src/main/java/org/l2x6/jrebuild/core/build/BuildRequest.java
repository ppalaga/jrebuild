package org.l2x6.jrebuild.core.build;

import org.l2x6.jrebuild.api.scm.FqScmRef;

public record BuildRequest(
        Os os,
        Arch arch,
        BuildGroup buildGroup,
        FqScmRef scmRef,
        Reproducibility requiredReproducibility) {

}
