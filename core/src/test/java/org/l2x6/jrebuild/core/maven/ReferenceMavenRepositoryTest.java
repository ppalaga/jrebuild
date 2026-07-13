/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.common.StackTraceLessException;
import org.l2x6.jrebuild.core.build.service.TestEnvironment;
import org.l2x6.jrebuild.core.build.service.TestEnvironment.RemoteRepository;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.Gavtcf;

public class ReferenceMavenRepositoryTest {

    @Test
    void e2e() throws Exception {
        try (TestEnvironment testEnv = new TestEnvironment(getClass(), RemoteRepository.LOCAL)) {

            Gavtc gavtcTxt = Gavtc.of("org.l2x6.pom-tuner:pom-tuner:4.10.0:txt");
            Path gavtcTxtPathSha1 = testEnv.createLargeRemoteFile(gavtcTxt, 1024 * 1024 * 20 /* 20 MB */);

            Gavtc gavtcPom = Gavtc.of("org.l2x6.pom-tuner:pom-tuner:4.10.0:pom");
            Path remoteRepoDir = testEnv.remoteRepoDirectory();
            byte[] pomContent = Files.readAllBytes(remoteRepoDir.resolve(gavtcPom.getRepositoryPath()));
            String pomSha1HexString = sha1Hex(pomContent);

            Assertions.assertThat(pomSha1HexString)
                    .isEqualTo(Files.readString(remoteRepoDir.resolve(gavtcPom.getRepositoryPath() + ".sha1")));

            Path localMavenRepo = testEnv.m2Directory();
            Path localRefRepo = testEnv.localRefRepoDirectory();

            // Ensure both local repos are empty
            final String sha1RelPath = gavtcPom.getRepositoryPath() + ".sha1";
            Assertions.assertThat(localRefRepo.resolve(sha1RelPath)).doesNotExist();
            Assertions.assertThat(localRefRepo.resolve(gavtcPom.getRepositoryPath())).doesNotExist();
            Assertions.assertThat(localMavenRepo.resolve(sha1RelPath)).doesNotExist();
            Assertions.assertThat(localMavenRepo.resolve(gavtcPom.getRepositoryPath())).doesNotExist();

            ReferenceMavenRepository repo = testEnv.getReferenceMavenRepository();
            {
                Gavtcf result = repo.resolve(gavtcPom).await().indefinitely();

                // Check the effects in the local repos
                Assertions.assertThat(localRefRepo.resolve(gavtcPom.getRepositoryPath())).doesNotExist();
                Assertions.assertThat(localRefRepo.resolve(sha1RelPath))
                        .isRegularFile()
                        .hasContent(pomSha1HexString);

                Assertions.assertThat(localMavenRepo.resolve(gavtcPom.getRepositoryPath())).isRegularFile();
                Assertions.assertThat(localMavenRepo.resolve(sha1RelPath))
                        .isRegularFile()
                        .hasContent(pomSha1HexString);

                // Check the result

                assertResult(localMavenRepo, gavtcPom, pomContent, result);
            }

            /*
             * now remove the file from the remote to make sure the server is not hit when the file in local Maven repo
             * has correct sha1
             */
            testEnv.hideRemote(gavtcPom.getRepositoryPath());
            testEnv.hideRemote(gavtcPom.getRepositoryPath() + ".sha1");
            /* The second attempt must pass without any of the files being available remotely */
            {
                final Gavtcf result = repo.resolve(gavtcPom).await().indefinitely();
                assertResult(localMavenRepo, gavtcPom, pomContent, result);
            }

            /* Unhide the files in the remote repo */
            testEnv.unhideRemote(gavtcPom.getRepositoryPath());
            testEnv.unhideRemote(gavtcPom.getRepositoryPath() + ".sha1");

            /* Damage the artifact in local Maven repo */
            Files.writeString(localMavenRepo.resolve(gavtcPom.getRepositoryPath()), "foo bar");
            /* The file must be taken from localRefRepo */
            {
                final Gavtcf result = repo.resolve(gavtcPom).await().indefinitely();
                assertResult(localRefRepo, gavtcPom, pomContent, result);
                Assertions.assertThat(localRefRepo.resolve(gavtcPom.getRepositoryPath())).hasBinaryContent(pomContent);
            }

            final Path gavtcTxtPath = testEnv.remoteRepoDirectory().resolve(gavtcTxt.getRepositoryPath());
            /* Download a large file */
            {
                final Gavtcf result = repo.resolve(gavtcTxt).await().indefinitely();
                Assertions.assertThat(result.getFile())
                        .isEqualTo(localMavenRepo.resolve(gavtcTxt.getRepositoryPath()))
                        .hasSameBinaryContentAs(gavtcTxtPath);
            }
            /* Break the sha1 in the remote repo and the download must fail */
            {
                Path localPath = Path.of("target/pom-tuner-4.10.0.txt").toAbsolutePath().normalize();
                Assertions
                        .assertThatThrownBy(
                                () -> repo.download(gavtcTxt.getRepositoryPath(), localPath, pomSha1HexString).await()
                                        .indefinitely())
                        .isInstanceOf(StackTraceLessException.class)
                        .hasMessage("SHA1 mismatch for " + testEnv.referenceRepoBaseUri()
                                + "/org/l2x6/pom-tuner/pom-tuner/4.10.0/pom-tuner-4.10.0.txt: expected " + pomSha1HexString
                                + " but got " + Files.readString(gavtcTxtPathSha1));

                /* The same with the public API */
                Gavtc gavtcAdoc = Gavtc.of("org.l2x6.pom-tuner:pom-tuner:4.10.0:adoc");
                Path gavtcAdocPath = remoteRepoDir.resolve(gavtcAdoc.getRepositoryPath());
                Path gavtcAdocPathSha1 = Path.of(gavtcAdocPath.toString() + ".sha1");
                Files.copy(gavtcTxtPath, gavtcAdocPath);
                Files.writeString(gavtcAdocPathSha1, pomSha1HexString); // intentionally incorrect
                Assertions
                        .assertThatThrownBy(() -> repo.resolve(gavtcAdoc).await().indefinitely())
                        .isInstanceOf(StackTraceLessException.class)
                        .hasMessage("SHA1 mismatch for " + testEnv.referenceRepoBaseUri()
                                + "/org/l2x6/pom-tuner/pom-tuner/4.10.0/pom-tuner-4.10.0.adoc: expected " + pomSha1HexString
                                + " but got " + Files.readString(gavtcTxtPathSha1));
            }
        }

    }

    private void assertResult(Path expectedRepo, Gavtc gavtc, byte[] expectedContent, Gavtcf actual) {
        actual.toGavtc().equals(gavtc);
        Assertions.assertThat(actual.getFile())
                .isEqualTo(expectedRepo.resolve(gavtc.getRepositoryPath()));
        Assertions.assertThat(actual.getFile())
                .hasBinaryContent(expectedContent);
    }

    static String sha1Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
