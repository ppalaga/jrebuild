/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central.api;

import java.nio.file.Path;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.reproducible.central.Shfmt;
import org.l2x6.jrebuild.reproducible.central.api.Buildspec.Newline;

class BuildspecTest {
    private static final Logger log = Logger.getLogger(BuildspecTest.class);

    private static Shfmt shfmt;

    @BeforeAll
    static void beforeAll() {
        UUID uuid = UUID.randomUUID();
        Path cacheDir = Path.of("target/cache-" + uuid).toAbsolutePath().normalize();
        shfmt = new Shfmt(cacheDir);
    }

    @Test
    void minimal() {

        Buildspec actual = mimimalBuilder("");
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

    static Buildspec mimimalBuilder(String additional) {

        return Buildspec.of(shfmt, Path.of("foo.buildspec"), """
                groupId=g
                artifactId=a
                version="1.2.3"
                display=$groupId:$artifactId
                gitRepo=https://github.com/foo/bar
                gitTag=v1.2.3
                tool=mvn
                jdk=17
                newline=crlf
                command=\"mvn clean deploy\"
                buildinfo=foo/bar
                """
                + "\n" + additional);
    }

    @Test
    void resolve() {
        Buildspec spec = mimimalBuilder(

                """
                        command=$groupId:$artifactId:$version
                        tool=${groupId}:${artifactId}
                        groupId=g
                        artifactId=a
                        version=1.2.3
                        """);
        Assertions.assertThat(spec.command()).isEqualTo("g:a:1.2.3");
        Assertions.assertThat(spec.tool()).isEqualTo("g:a");
        Assertions.assertThat(spec.groupId()).isEqualTo("g");
        Assertions.assertThat(spec.artifactId()).isEqualTo("a");

    }

    @Test
    void trailingComment() {
        Buildspec spec = mimimalBuilder("""
                newline=crlf # comment
                command="foo # bar"
                        """);
        Assertions.assertThat(spec.newline()).isEqualTo(Newline.crlf);
        Assertions.assertThat(spec.command()).isEqualTo("foo # bar");

    }

    @Test
    void multilineComment() {
        {
            Buildspec spec = mimimalBuilder("""
                    command="foo
                    bar"
                        """);
            Assertions.assertThat(spec.command()).isEqualTo("foo\nbar");
        }
        {
            Buildspec spec = mimimalBuilder("""
                    command="foo
                    bar" #comment
                        """);
            Assertions.assertThat(spec.command()).isEqualTo("foo\nbar");
        }

    }

    @Test
    void commonsDaemon() {
        Buildspec bs = of("commons-daemon-1.5.1.buildspec");
        Assertions.assertThat(bs.groupId()).isEqualTo("commons-daemon");
        Assertions.assertThat(bs.artifactId()).isEqualTo("commons-daemon");
        Assertions.assertThat(bs.display()).isEqualTo("commons-daemon:commons-daemon");
        Assertions.assertThat(bs.version()).isEqualTo("1.5.1");
        Assertions.assertThat(bs.gitRepo()).isEqualTo("https://github.com/apache/commons-daemon.git");
        Assertions.assertThat(bs.gitTag()).isEqualTo("commons-daemon-1.5.1");
        Assertions.assertThat(bs.tool()).isEqualTo("mvn");
        Assertions.assertThat(bs.jdk()).isEqualTo("11");
        Assertions.assertThat(bs.newline()).isEqualTo(Newline.lf);
        Assertions.assertThat(bs.umask()).isEqualTo("022");
        Assertions.assertThat(bs.command()).isEqualTo(
                "mvn clean verify -Prelease -Dcommons.release.isDistModule=false -DskipTests -Dgpg.skip -Dbuildinfo.ignore='*/*.spdx.json,*/*-tests.jar,*/*-test-sources.jar'");
        Assertions.assertThat(bs.buildinfo()).isEqualTo("target/commons-daemon-1.5.1.buildinfo");
        Assertions.assertThat(bs.execBefore()).isEqualTo("( cd src/native/unix \\\n  && sh support/buildconf.sh )");
        Assertions.assertThat(bs.issue()).isNull();

    }

    @Test
    void flexmarkJava() {
        Buildspec bs = of("flexmark-java-0.64.8.buildspec");
        Assertions.assertThat(bs.groupId()).isEqualTo("com.vladsch.flexmark");
        Assertions.assertThat(bs.artifactId()).isEqualTo("flexmark-java");
        Assertions.assertThat(bs.display()).isEqualTo("com.vladsch.flexmark:flexmark-java");
        Assertions.assertThat(bs.version()).isEqualTo("0.64.8");
        Assertions.assertThat(bs.gitRepo()).isEqualTo("https://github.com/vsch/flexmark-java.git");
        Assertions.assertThat(bs.gitTag()).isEqualTo("cc3a2f59ba6e532833f4805f8134b4dc966ff837");
        Assertions.assertThat(bs.tool()).isEqualTo("mvn");
        Assertions.assertThat(bs.jdk()).isEqualTo("11");
        Assertions.assertThat(bs.newline()).isEqualTo(Newline.lf);
        Assertions.assertThat(bs.umask()).isEqualTo("0002");
        Assertions.assertThat(bs.command()).isEqualTo(
                "mvn -Pdeploy clean package -DskipTests -Dmaven.javadoc.skip -Dgpg.skip -Dbuildinfo.detect.skip=false");
        Assertions.assertThat(bs.buildinfo()).isEqualTo("target/flexmark-java-0.64.8.buildinfo");
        Assertions.assertThat(bs.execBefore()).isNull();
        Assertions.assertThat(bs.issue()).isNull();

    }

    @Test
    void avajeInject() {
        Buildspec bs = of("avaje-inject-12.1-javax.buildspec");
        Assertions.assertThat(bs.groupId()).isEqualTo("io.avaje");
        Assertions.assertThat(bs.artifactId()).isEqualTo("avaje-inject-parent");
        Assertions.assertThat(bs.display()).isEqualTo("io.avaje:$(echo \"avaje-inject-parent\"|sed -e \"s/-parent//\")");
        Assertions.assertThat(bs.version()).isEqualTo("12.1-javax");
        Assertions.assertThat(bs.gitRepo()).isEqualTo("https://github.com/avaje/avaje-inject.git");
        Assertions.assertThat(bs.gitTag()).isEqualTo("$(echo \"12.1-javax\"|cut -d '-' -f 1)");
        Assertions.assertThat(bs.tool()).isEqualTo("mvn");
        Assertions.assertThat(bs.jdk()).isEqualTo("21");
        Assertions.assertThat(bs.newline()).isEqualTo(Newline.lf);
        Assertions.assertThat(bs.umask()).isEqualTo("0002");
        Assertions.assertThat(bs.command()).isEqualTo(
                "mvn -Pcentral clean package -DskipTests -Dmaven.javadoc.skip -Dgpg.skip");
        Assertions.assertThat(bs.buildinfo()).isEqualTo("target/avaje-inject-parent-12.1-javax.buildinfo");
        Assertions.assertThat(bs.execBefore()).isEqualTo(
                "sed -i 's/-i.. -e/-i -E/' jakarta-to-javax.sh && sed -i -e 's/09T08:53:26Z/09T08:57:56Z/' pom.xml && ./jakarta-to-javax.sh && for f in \\$(find * -name '*.java' -print) ; do cp \\$f \\$f-e ; done && rm inject-generator/src/main/java/io/avaje/inject/generator/IncludeAnnotations.java-e");
        Assertions.assertThat(bs.issue()).isNull();

    }

    @Test
    void junit5() {
        Buildspec bs = of("junit5-5.10.5.buildspec");
        Assertions.assertThat(bs.groupId()).isEqualTo("org.junit");
        Assertions.assertThat(bs.artifactId()).isEqualTo("junit-bom");
        Assertions.assertThat(bs.display()).isEqualTo("org.junit:junit-bom");
        Assertions.assertThat(bs.version()).isEqualTo("5.10.5");
        Assertions.assertThat(bs.gitRepo()).isEqualTo("https://github.com/junit-team/junit5.git");
        Assertions.assertThat(bs.gitTag()).isEqualTo("r5.10.5");
        Assertions.assertThat(bs.tool()).isEqualTo("gradle");
        Assertions.assertThat(bs.jdk()).isEqualTo("17");
        Assertions.assertThat(bs.newline()).isEqualTo(Newline.lf);
        Assertions.assertThat(bs.umask()).isEqualTo("0002");
        Assertions.assertThat(bs.command()).isEqualTo(
                "./gradlew clean assemble -x test -x signMavenPublication --no-daemon --no-build-cache -Ppublishing.signArtifacts=false publishToMavenLocal -Pmanifest.buildTimestamp='2024-10-04 14:33:46.529+0200' -Pmanifest.createdBy='17.0.10 (Eclipse Adoptium 17.0.10+7)'");
        Assertions.assertThat(bs.buildinfo()).isEqualTo("junit-bom-5.10.5.buildinfo");
        Assertions.assertThat(bs.execBefore()).isNull();
        Assertions.assertThat(bs.issue()).isNull();

    }

    @Test
    void hibernateReactive() {
        Buildspec bs = of("hibernate-reactive-3.0.8.Final.buildspec");
        Assertions.assertThat(bs.groupId()).isEqualTo("org.hibernate.reactive");
        Assertions.assertThat(bs.artifactId()).isEqualTo("hibernate-reactive-core");
        Assertions.assertThat(bs.display()).isEqualTo("org.hibernate.reactive:hibernate-reactive-core");
        Assertions.assertThat(bs.version()).isEqualTo("3.0.8.Final");
        Assertions.assertThat(bs.gitRepo()).isEqualTo("https://github.com/hibernate/hibernate-reactive.git");
        Assertions.assertThat(bs.gitTag()).isEqualTo("$(echo \"3.0.8.Final\"|sed -e 's/.Final//')");
        Assertions.assertThat(bs.tool()).isEqualTo("gradle");
        Assertions.assertThat(bs.jdk()).isEqualTo("17");
        Assertions.assertThat(bs.newline()).isEqualTo(Newline.lf);
        Assertions.assertThat(bs.umask()).isEqualTo("022");
        Assertions.assertThat(bs.command()).isEqualTo(
                "./gradlew --no-daemon --no-build-cache clean publishToMavenLocal");
        Assertions.assertThat(bs.buildinfo()).isEqualTo("hibernate-reactive-core-3.0.8.Final.buildinfo");
        Assertions.assertThat(bs.execBefore()).isNull();
        Assertions.assertThat(bs.issue()).isNull();

    }

    @Test
    void of() {
        Buildspec mvn = of("maven-4.0.0-rc-1.buildspec");
        Assertions.assertThat(mvn.command()).isEqualTo(
                "mvn -Papache-release clean package -DskipTests -Dmaven.javadoc.skip -Dgpg.skip -DbuildNumber=$(git ls-remote --tags https://github.com/apache/maven.git|grep \"refs/tags/maven-4.0.0-rc-1^{}$\"|cut -f 1)");
    }

    static Buildspec of(String bspecName) {
        Path bspec = Path.of("src/test/resources/buildspec/" + bspecName);
        return Buildspec.of(shfmt, bspec);
    }
}
