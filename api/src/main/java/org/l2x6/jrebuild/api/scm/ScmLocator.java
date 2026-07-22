/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import java.util.List;
import org.l2x6.pom.tuner.model.Gav;

public interface ScmLocator {
    /**
     * Find the SCM repository and Tag (or other kind of SCM reference) from which the given {@link Gav} can be built.
     *
     * The following outcomes are possible:
     * <ul>
     * <li>Empty list - this {@link ScmLocator} cannot provide any SCM information about the given {@link Gav}
     * <li>A list with one element, that is not a failure - this SCM was able to find reliable SCM information
     * <li>A list with more then one element - the elements must me failures
     * </ul>
     *
     * @param  gav
     * @return     a list of {@link AnnotatedFqScmRef}
     */
    List<AnnotatedFqScmRef> locate(Gav gav);
}
