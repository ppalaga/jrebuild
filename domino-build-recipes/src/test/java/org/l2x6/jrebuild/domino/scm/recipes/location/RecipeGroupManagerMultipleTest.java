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

public class RecipeGroupManagerMultipleTest {
    static RecipeGroupManager manager;

    @BeforeAll
    public static void setup() throws Exception {
        Path opath = Paths.get(RecipeGroupManagerMultipleTest.class.getClassLoader().getResource("override").toURI());
        Path path = Paths.get(RecipeGroupManagerMultipleTest.class.getClassLoader().getResource("test-recipes").toURI());
        manager = new RecipeGroupManager(List.of(new RecipeLayoutManager(opath), new RecipeLayoutManager(path)));
    }

    @Test
    public void testGroupIdBasedRecipe() throws IOException {
        Gav req = new Gav("io.test", "test", "1.0");
        RecipeFile result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/test-override/test.git",
                readScmUrl(result.recipeFile()));

        Assertions.assertTrue(
                BuildRecipe.SCM.getHandler().parse(result.recipeFile()).isPrivateRepo());

        req = new Gav("io.test.acme", "test-acme", "1.0");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/test-override/test-acme.git",
                readScmUrl(result.recipeFile()));
        Assertions.assertFalse(
                BuildRecipe.SCM.getHandler().parse(result.recipeFile()).isPrivateRepo());

        req = new Gav("io.foo", "test-foo", "1.0");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/foo/foo.git",
                readScmUrl(result.recipeFile()));
        Assertions.assertFalse(
                BuildRecipe.SCM.getHandler().parse(result.recipeFile()).isPrivateRepo());
    }

    @Test
    public void testVersionOverride() {
        //the original override should still work
        Gav req = new Gav("io.quarkus", "quarkus-core", "1.0-alpha1");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/quarkus.git",
                readScmUrl(result.recipeFile()));

        //but now we have added a new one as well
        req = new Gav("io.quarkus", "quarkus-core", "1.0-alpha2");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/quarkus.git",
                readScmUrl(result.recipeFile()));
    }

    @Test
    public void testArtifactOverride() {
        //this should still work as normal, it is not overriden
        Gav req = new Gav("io.quarkus", "quarkus-gizmo", "1.0");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/quarkusio/gizmo.git",
                readScmUrl(result.recipeFile()));

        req = new Gav("io.test", "test-gizmo", "1.0");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/test/gizmo.git",
                readScmUrl(result.recipeFile()));
    }

    @Test
    public void testArtifactAndVersionOverride() {
        //same here
        Gav req = new Gav("io.quarkus", "quarkus-gizmo", "1.0-alpha1");
        var result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/gizmo.git",
                readScmUrl(result.recipeFile()));

        req = new Gav("io.test", "test-gizmo", "1.0-alpha1");
        result = manager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/stuartwdouglas/gizmo.git",
                readScmUrl(result.recipeFile()));

        req = new Gav("io.test", "test-gizmo", "0.9");
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
