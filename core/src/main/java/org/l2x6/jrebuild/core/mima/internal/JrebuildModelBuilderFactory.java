/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.mima.internal;

import java.util.Map;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.inheritance.DefaultInheritanceAssembler;
import org.apache.maven.model.inheritance.InheritanceAssembler;

public class JrebuildModelBuilderFactory extends DefaultModelBuilderFactory {

    @Override
    protected InheritanceAssembler newInheritanceAssembler() {
        return new JrebuildDefaultInheritanceAssembler();
    }

    static class JrebuildDefaultInheritanceAssembler extends DefaultInheritanceAssembler {

        private InheritanceModelMerger merger = new JrebuildInheritanceModelMerger();

        @Override
        public void assembleModelInheritance(
                Model child, Model parent, ModelBuildingRequest request, ModelProblemCollector problems) {
            merger.merge(child, parent, false, Map.of());
        }

        static class JrebuildInheritanceModelMerger extends InheritanceModelMerger {

        }
    }

}
