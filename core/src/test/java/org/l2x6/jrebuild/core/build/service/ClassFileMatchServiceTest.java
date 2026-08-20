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
                        """
                                flags: public super -> final public super
                                name: org.l2x6.jrebuild.core.build.service.samples.ClassPerson1 -> org.l2x6.jrebuild.core.build.service.samples.ClassPerson2
                                fields:
                                    firstName:Ljava/lang/String;:
                                        flags: final private -> <none>
                                methods:
                                    <init>(Ljava/lang/String;Ljava/lang/String;)V
                                        code:
                                            @@ -7,16 +7,16 @@
                                               - {start: 9, line number: 10}
                                               - {start: 14, line number: 11}
                                             local variables:
                                            -  - {start: 0, end: 15, slot: 0, name: this, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson1;}
                                            +  - {start: 0, end: 15, slot: 0, name: this, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson2;}
                                               - {start: 0, end: 15, slot: 1, name: firstName, type: Ljava/lang/String;}
                                               - {start: 0, end: 15, slot: 2, name: lastName, type: Ljava/lang/String;}
                                             //stack map frame @0: {locals: [THIS, java/lang/String, java/lang/String], stack: []}
                                            -0: {opcode: ALOAD_0, slot: 0, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson1;, variable name: this}
                                            +0: {opcode: ALOAD_0, slot: 0, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson2;, variable name: this}
                                             1: {opcode: INVOKESPECIAL, owner: java/lang/Object, method name: <init>, method type: ()V}
                                            -4: {opcode: ALOAD_0, slot: 0, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson1;, variable name: this}
                                            +4: {opcode: ALOAD_0, slot: 0, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson2;, variable name: this}
                                             5: {opcode: ALOAD_1, slot: 1, type: Ljava/lang/String;, variable name: firstName}
                                            -6: {opcode: PUTFIELD, owner: org/l2x6/jrebuild/core/build/service/samples/ClassPerson1, field name: firstName, field type: Ljava/lang/String;}
                                            -9: {opcode: ALOAD_0, slot: 0, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson1;, variable name: this}
                                            +6: {opcode: PUTFIELD, owner: org/l2x6/jrebuild/core/build/service/samples/ClassPerson2, field name: firstName, field type: Ljava/lang/String;}
                                            +9: {opcode: ALOAD_0, slot: 0, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson2;, variable name: this}
                                             10: {opcode: ALOAD_2, slot: 2, type: Ljava/lang/String;, variable name: lastName}
                                            -11: {opcode: PUTFIELD, owner: org/l2x6/jrebuild/core/build/service/samples/ClassPerson1, field name: lastName, field type: Ljava/lang/String;}
                                            +11: {opcode: PUTFIELD, owner: org/l2x6/jrebuild/core/build/service/samples/ClassPerson2, field name: lastName, field type: Ljava/lang/String;}
                                             14: {opcode: RETURN}
                                    toString()Ljava/lang/String;
                                        code:
                                            @@ -4,11 +4,11 @@
                                             line numbers:
                                               - {start: 0, line number: 15}
                                             local variables:
                                            -  - {start: 0, end: 14, slot: 0, name: this, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson1;}
                                            -//stack map frame @0: {locals: [org/l2x6/jrebuild/core/build/service/samples/ClassPerson1], stack: []}
                                            -0: {opcode: ALOAD_0, slot: 0, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson1;, variable name: this}
                                            -1: {opcode: GETFIELD, owner: org/l2x6/jrebuild/core/build/service/samples/ClassPerson1, field name: firstName, field type: Ljava/lang/String;}
                                            -4: {opcode: ALOAD_0, slot: 0, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson1;, variable name: this}
                                            -5: {opcode: GETFIELD, owner: org/l2x6/jrebuild/core/build/service/samples/ClassPerson1, field name: lastName, field type: Ljava/lang/String;}
                                            -8: {opcode: INVOKEDYNAMIC, name: makeConcatWithConstants, descriptor: (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;, bootstrap method: STATIC java/lang/invoke/StringConcatFactory::makeConcatWithConstants, arguments: ['\\u0001 \\u0001']}
                                            +  - {start: 0, end: 14, slot: 0, name: this, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson2;}
                                            +//stack map frame @0: {locals: [org/l2x6/jrebuild/core/build/service/samples/ClassPerson2], stack: []}
                                            +0: {opcode: ALOAD_0, slot: 0, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson2;, variable name: this}
                                            +1: {opcode: GETFIELD, owner: org/l2x6/jrebuild/core/build/service/samples/ClassPerson2, field name: firstName, field type: Ljava/lang/String;}
                                            +4: {opcode: ALOAD_0, slot: 0, type: Lorg/l2x6/jrebuild/core/build/service/samples/ClassPerson2;, variable name: this}
                                            +5: {opcode: GETFIELD, owner: org/l2x6/jrebuild/core/build/service/samples/ClassPerson2, field name: lastName, field type: Ljava/lang/String;}
                                            +8: {opcode: INVOKEDYNAMIC, name: makeConcatWithConstants, descriptor: (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;, bootstrap method: STATIC java/lang/invoke/StringConcatFactory::makeConcatWithConstants, arguments: ['firstName=\\u0001, lastName=\\u0001]']}
                                             13: {opcode: ARETURN}
                                """
                                .trim(),
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
