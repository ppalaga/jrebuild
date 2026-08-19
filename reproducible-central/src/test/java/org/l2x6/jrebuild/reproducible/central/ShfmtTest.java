/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.cliassured.CliAssured;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.os.OsArch;
import org.l2x6.jrebuild.reproducible.central.Shfmt.ShfmtOsArchDetector;

public class ShfmtTest {
    @Test
    void install() {
        UUID uuid = UUID.randomUUID();
        Path cacheDir = Path.of("target/cache-" + uuid).toAbsolutePath().normalize();

        final OsArch currentOsArch = OsArch.current();
        final ShfmtOsArchDetector detector = new ShfmtOsArchDetector();
        final Path cachedShfmtBinariesDir = cacheDir.resolve("shfmt");

        {
            /* The binary is not cached initially */
            Optional<Path> shfmtBinary = Shfmt.findCachedBinary(currentOsArch, detector, cachedShfmtBinariesDir);
            Assertions.assertThat(shfmtBinary).isEmpty();
        }

        /* Install it */
        String binPath = Shfmt.install(currentOsArch, detector, cachedShfmtBinariesDir);

        /* The binary must be cached now */
        Optional<Path> shfmtBinary = Shfmt.findCachedBinary(currentOsArch, detector, cachedShfmtBinariesDir);
        Assertions.assertThat(shfmtBinary).isPresent();
        Assertions.assertThat(shfmtBinary.get().toString()).isEqualTo(binPath);
        Assertions.assertThat(shfmtBinary.get()).isExecutable();

        /* Make sure that the binary actually works */
        CliAssured.command(binPath, "--help")
                .then()
                .stderr()
                .hasLinesContaining("shfmt formats shell programs")
                .execute()
                .assertSuccess();
    }
}
