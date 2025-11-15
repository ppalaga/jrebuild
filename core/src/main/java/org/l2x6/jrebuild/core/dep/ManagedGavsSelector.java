/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.GavtcsSet;

public class ManagedGavsSelector {
    private final Function<Gav, Model> getEffectiveModel;

    public ManagedGavsSelector(Function<Gav, Model> getEffectiveModel) {
        super();
        this.getEffectiveModel = getEffectiveModel;
    }

    public Set<Gavtc> select(Gav bom, GavtcsSet filters) {

        final Model model = getEffectiveModel.apply(bom);
        final List<Dependency> deps = getManagedDependencies(model);
        if (deps != null && !deps.isEmpty()) {
            return Collections.unmodifiableSet(deps.stream()
                    .map(JrebuildUtils::toGavtc)
                    .filter(filters::contains)
                    .collect(Collectors.<Gavtc, Set<Gavtc>> toCollection(LinkedHashSet::new)));
        }
        return Collections.emptySet();
    }

    public static List<Dependency> getManagedDependencies(Model model) {
        final DependencyManagement dependencyManagement = model.getDependencyManagement();
        if (dependencyManagement != null) {
            final List<Dependency> deps = dependencyManagement.getDependencies();
            return deps == null ? Collections.emptyList() : deps;
        }
        return Collections.emptyList();
    }
}
