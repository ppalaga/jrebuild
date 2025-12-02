/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central.api;

import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.reproducible.central.api.Buildspec.Builder;
import org.l2x6.jrebuild.reproducible.central.api.Buildspec.Newline;

class BuildspecTest {
    private static final Logger log = Logger.getLogger(BuildspecTest.class);

    @Test
    void minimal() {

        Builder b = mimimalBuilder();

        Buildspec actual = b.build();
        Assertions.assertThat(actual.groupId()).isEqualTo("g");
        Assertions.assertThat(actual.artifactId()).isEqualTo("a");
        Assertions.assertThat(actual.version()).isEqualTo("1.2.3");
        Assertions.assertThat(actual.gitRepo()).isEqualTo("https://github.com/foo/bar");
        Assertions.assertThat(actual.gitTag()).isEqualTo("v1.2.3");
        Assertions.assertThat(actual.tool()).isEqualTo("mvn");
        Assertions.assertThat(actual.jdk()).isEqualTo("17");
        Assertions.assertThat(actual.newline()).isEqualTo(Newline.crlf);
        Assertions.assertThat(actual.command()).isEqualTo("mvn clean deploy");

    }

    static Builder mimimalBuilder() {
        Builder b = Buildspec.builder();
        b.line("groupId=g");
        b.line("artifactId=a");
        b.line("version=\"1.2.3\"");
        b.line("gitRepo=https://github.com/foo/bar");
        b.line("gitTag=v1.2.3");
        b.line("tool=mvn");
        b.line("jdk=17");
        b.line("newline=crlf");
        b.line("command=\"mvn clean deploy\"");
        b.line("buildinfo=foo/bar");
        return b;
    }

    @Test
    void resolve() {
        Builder b = mimimalBuilder();

        b
                .line("gav=$groupId:$artifactId:$version")
                .line("ga=${groupId}:${artifactId}")
                .line("g=${groupId}")
                .line("a=$artifactId");

        Assertions.assertThat(b.resolve("gav")).isEqualTo("g:a:1.2.3");
        Assertions.assertThat(b.resolve("ga")).isEqualTo("g:a");
        Assertions.assertThat(b.resolve("g")).isEqualTo("g");
        Assertions.assertThat(b.resolve("a")).isEqualTo("a");

    }

    @Test
    void trailingComment() {
        Builder b = Buildspec.builder();

        b.line("newline=crlf # comment");
        b.line("foo=\"foo # bar\"");
        b.assertFinished();

        Assertions.assertThat(b.resolve("newline")).isEqualTo("crlf");
        Assertions.assertThat(b.resolve("foo")).isEqualTo("foo # bar");

    }

    @Test
    void multilineComment() {
        {
            Builder b = Buildspec.builder();
            b.line("foo=\"foo");
            b.line("bar\"");
            b.assertFinished();
            Assertions.assertThat(b.resolve("foo")).isEqualTo("foo\nbar");
        }
        {
            Builder b = Buildspec.builder();
            b.line("foo=\"foo");
            b.line("bar\" #comment");
            b.assertFinished();
            Assertions.assertThat(b.resolve("foo")).isEqualTo("foo\nbar");
        }

    }

    @Test
    void of() {
        Buildspec mvn = of("maven-4.0.0-rc-1.buildspec");
        Assertions.assertThat(mvn.command()).isEqualTo(
                "mvn -Papache-release clean package -DskipTests -Dmaven.javadoc.skip -Dgpg.skip -DbuildNumber=$(git ls-remote --tags https://github.com/apache/${artifactId}.git | grep \"refs/tags/${artifactId}-${version}^{}$\" | cut -f 1)");
    }

    static Buildspec of(String bspecName) {
        Path bspec = Path.of("src/test/resources/buildspec/" + bspecName);
        return Buildspec.of(bspec);
    }
}
