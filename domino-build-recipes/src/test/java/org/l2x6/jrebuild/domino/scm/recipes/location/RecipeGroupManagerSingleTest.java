/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.location;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.domino.scm.recipes.BuildRecipe;
import org.l2x6.jrebuild.domino.scm.recipes.scm.ScmInfo;
import org.l2x6.pom.tuner.model.Gav;

public class RecipeGroupManagerSingleTest {
    static RecipeGroupManager manager;

    @BeforeAll
    public static void setup() throws Exception {
        //noinspection DataFlowIssue
        Path path = Paths.get(RecipeGroupManagerSingleTest.class.getClassLoader().getResource("test-recipes").toURI());
        manager = new RecipeGroupManager(List.of(new RecipeLayoutManager(path)));
    }

    @Test
    public void testGroupIdBasedRecipe() {
        Gav req = new Gav("io.quarkus", "quarkus-core", "1.0");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/quarkusio/quarkus.git",
                readScmUrl(result.recipeFile()));

        req = new Gav("io.quarkus.security", "quarkus-security", "1.0");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/quarkusio/quarkus-security.git",
                readScmUrl(result.recipeFile()));
    }

    @Test
    public void testVersionOverride() {
        Gav req = new Gav("io.quarkus", "quarkus-core", "1.0-alpha1");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/quarkus.git",
                readScmUrl(result.recipeFile()));
        req = new Gav("io.quarkus", "quarkus-core", "0.9");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/quarkus.git",
                readScmUrl(result.recipeFile()));
    }

    @Test
    public void testArtifactOverride() {
        Gav req = new Gav("io.quarkus", "quarkus-gizmo", "1.0");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/quarkusio/gizmo.git",
                readScmUrl(result.recipeFile()));
    }

    @Test
    public void testArtifactAndVersionOverride() {
        Gav req = new Gav("io.quarkus", "quarkus-gizmo", "1.0-alpha1");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/gizmo.git",
                readScmUrl(result.recipeFile()));
        req = new Gav("io.quarkus", "quarkus-gizmo", "0.9");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/gizmo.git",
                readScmUrl(result.recipeFile()));
    }

    @Test
    public void testNoGroupLevelBuild() {
        Gav req = new Gav("io.vertx", "not-real", "1.0");
        var result = manager.lookupScmInformation(req);
        Assertions.assertNull(result);
    }

    @Test
    public void testArtifactLZ4()
            throws IOException {
        Gav req = new Gav("org.lz4", "lz4", "1.8.0");
        RecipeFile scmLookup = manager.lookupScmInformation(req);
        var result = BuildRecipe.SCM.getHandler().parse(scmLookup.recipeFile());

        Assertions.assertNull(result.getBuildNameFragment());
        Assertions.assertEquals("https://github.com/lz4/lz4-java.git", result.getUri());

        req = new Gav("org.lz4", "lz4-pure-java", "1.8.0");
        scmLookup = manager.lookupScmInformation(req);
        result = BuildRecipe.SCM.getHandler().parse(scmLookup.recipeFile());

        Assertions.assertEquals("pureJava", result.getBuildNameFragment());
        Assertions.assertEquals("https://github.com/lz4/lz4-java.git", result.getUriWithoutFragment());
    }

    private String readScmUrl(Path scmPath) {
        if (scmPath == null) {
            return "";
        }
        try {
            ScmInfo parse = BuildRecipe.SCM.getHandler().parse(scmPath);
            if (parse == null) {
                return "";
            }
            return parse.getUri();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
