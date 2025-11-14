/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.location;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.domino.scm.recipes.BuildRecipe;
import org.l2x6.jrebuild.domino.scm.recipes.scm.ScmInfo;
import org.l2x6.pom.tuner.model.Gav;
import org.yaml.snakeyaml.Yaml;

public class RecipeGroupManagerAddTest {

    @Test
    public void testAddingBuildRecipe() throws Exception {
        Path temp = Files.createTempDirectory("tests");
        RecipeLayoutManager manager = new RecipeLayoutManager(temp);
        ScmInfo add = new ScmInfo("git", "https://github.com/quarkusio/quarkus.git");
        manager.writeArtifactData(new AddRecipeRequest<>(BuildRecipe.SCM, add, "io.quarkus", null, null));

        add = new ScmInfo("git", "https://github.com/quarkusio/quarkus-security.git");
        manager.writeArtifactData(new AddRecipeRequest<>(BuildRecipe.SCM, add, "io.quarkus.security", null, null));

        RecipeGroupManager groupManager = new RecipeGroupManager(List.of(manager));
        Gav req = new Gav("io.quarkus", "quarkus-core", "1.0");
        List<Path> result = groupManager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/quarkusio/quarkus.git",
                readScmUrl(result.get(0)));

        req = new Gav("io.quarkus.security", "quarkus-security", "1.0");
        result = groupManager.lookupScmInformation(req);
        Assertions.assertEquals("https://github.com/quarkusio/quarkus-security.git",
                readScmUrl(result.get(0)));
    }

    private String readScmUrl(Path scmPath) {

        Yaml yaml = new Yaml();
        String uri = null;
        try (InputStream in = Files.newInputStream(scmPath)) {
            ScmInfo contents = yaml.loadAs(new InputStreamReader(in), ScmInfo.class);
            if (contents != null) {
                uri = contents.getUri();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (uri == null) {
            return ""; //use the empty string for this case
        }
        return uri;
    }

}
