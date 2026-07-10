package org.l2x6.jrebuild.core.build.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.pom.tuner.model.Ga;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

/**
 * Layout:
 *
 * <pre>{@code
 * builds
 * + org/group1/project/version/<tag>
 * | + 2026-03-04T11-22-33 // attempt timestamp
 * | | +- build-report.yaml
 * | + 2026-03-05T22-33-44 // attempt timestamp
 * | | +- build-report.yaml
 * }</pre>
 *
 */
public record BuildMetadataLayout(Path buildsDirectory) {
    private static final DateTimeFormatter DIR_FORMAT = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .append(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral('-')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral('-')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(NANO_OF_SECOND, 0, 9, true)
            .toFormatter(Locale.ROOT);

    public static String format(ZonedDateTime ts) {
        return DIR_FORMAT.format(ts);
    }

    public Path getOrCreateBuildDirectory(BuildGroup buildGroup) {
        final String revision = buildGroup.scmRef().scmRef().revisionOrUnknownRevision();
        Ga mainArtifact = buildGroup.findMainArtifact().toGa();
        Path result = buildsDirectory.resolve(mainArtifact.getRepositoryPath()).resolve(revision);
        if (!Files.isDirectory(result)) {
            try {
                Files.createDirectories(result);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not create " + result, e);
            }
        }
        return result;
    }

}
