/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import org.l2x6.pom.tuner.model.Gav;

public interface ScmLocator {
    ScmRef locate(Gav gav);
}
