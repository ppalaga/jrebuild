/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.build.Resource;
import org.l2x6.jrebuild.core.build.Resource.FileResource;
import org.l2x6.jrebuild.core.build.ResourceMatch;
import org.l2x6.jrebuild.core.build.ResourceMatchLevel;
import org.l2x6.jrebuild.core.build.service.ResourceMatchService.BaseResourceMatchService;
import org.l2x6.jrebuild.core.build.service.ResourceMatchService.BaseResourceMatchService.ClassFileMatchService;
import org.l2x6.jrebuild.core.build.service.samples.ClassPerson1;
import org.l2x6.jrebuild.core.build.service.samples.ClassPerson2;

public class ClassFileMatchServiceTest {

    @Test
    void classFileMatchService() throws IOException {
        ResourceMatchService service = new ClassFileMatchService();
        assertCompareClassFiles(service);
    }

    @Test
    void classFileResourceMatchService() throws IOException {
        ResourceMatchService service = new ResourceMatchService.BaseResourceMatchService();
        assertCompareClassFiles(service);
    }

    static void assertCompareClassFiles(ResourceMatchService service) throws IOException {
        assertCompareSameClassFile(service, Resource.class);
        assertCompareSameClassFile(service, ResourceMatchLevel.class);
        assertCompareSameClassFile(service, BaseResourceMatchService.class);

        assertCompareClassFile(
                service,
                testPath(ClassPerson1.class),
                testPath(ClassPerson2.class),
                new ResourceMatch(
                        "target/test-classes/org/l2x6/jrebuild/core/build/service/samples/ClassPerson2.class",
                        ResourceMatchLevel.BUILDABLE,
                        Files.readString(
                                Path.of("src/test/resources/ClassFileMatchServiceTest/ClassPerson2-diff-expected.txt")),
                        List.of()));
    }

    static String path(Class<?> cl) {
        return "target/classes/" + cl.getName().replace('.', '/') + ".class";
    }

    static String testPath(Class<?> cl) {
        return "target/test-classes/" + cl.getName().replace('.', '/') + ".class";
    }

    static void assertCompareSameClassFile(
            ResourceMatchService service, Class<?> cl) throws IOException {
        String path = path(cl);
        assertCompareClassFile(service, path, path, ResourceMatchLevel.PERFECT.match(path));
    }

    static void assertCompareClassFile(
            ResourceMatchService service,
            String a,
            String b,
            ResourceMatch expected) throws IOException {
        Path ap = Path.of(a);
        Path bp = Path.of(b);
        {
            Resource ar = Resource.of(ap.toString().replace('\\', '/'), Files.readAllBytes(ap));
            Resource br = Resource.of(bp.toString().replace('\\', '/'), Files.readAllBytes(bp));
            ResourceMatch actual = service.compare(ar, br);
            try {
                Assertions.assertThat(actual).isEqualTo(expected);
            } catch (AssertionError e) {
                Files.writeString(Path.of("target/match-expected.txt"), expected.toString());
                Files.writeString(Path.of("target/match-actual.txt"), actual.toString());
                throw e;
            }
        }
        {
            Resource ar = new FileResource(ap, ap.toString());
            Resource br = new FileResource(bp, bp.toString());
            ResourceMatch actual = service.compare(ar, br);
            try {
                Assertions.assertThat(actual).isEqualTo(expected);
            } catch (AssertionError e) {
                Files.writeString(Path.of("target/match-expected.txt"), expected.toString());
                Files.writeString(Path.of("target/match-actual.txt"), actual.toString());
                throw e;
            }
        }
    }

}
