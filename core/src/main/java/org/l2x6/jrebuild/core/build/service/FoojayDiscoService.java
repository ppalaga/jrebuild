package org.l2x6.jrebuild.core.build.service;

import io.foojay.api.discoclient.DiscoClient;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import org.l2x6.jrebuild.core.build.JavaDistroAndVersion;

import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FoojayDiscoService {

    private final DiscoClient discoClient;

    public FoojayDiscoService() {
        super();
        this.discoClient = new DiscoClient();
    }

    public Uni<JavaDistroAndVersion> latestJavaDistroAndVersionAsOf(ZonedDateTime timestamp) {
        throw new UnsupportedOperationException();
    }

    public Uni<Set<String>> findDistributionNamesThatSupportVersion(String version) {
        return Uni.createFrom()
            .item(() -> discoClient
                .getDistributionsThatSupportVersion(version)
                .stream()
                .map(d -> d.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(() -> (Set<String>) new TreeSet<String>())))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    public String vendorToDistro(String vendor) {
        KnownJvmVendor knownVendor = KnownJvmVendor.of(vendor);
        if (knownVendor != null && knownVendor != KnownJvmVendor.UNKNOWN) {
            return knownVendor.foojayDistroName();
        }
        return null;
        /*
         * Sun Microsystems Inc. (pre-2010, old jars)
         * Oracle Corporation (Oracle JDK — and a huge number of OpenJDK builds that never changed the vendor property)
         * Eclipse Adoptium / AdoptOpenJDK (Temurin and its predecessor)
         * Azul Systems, Inc. (Zulu)
         * GraalVM Community / Oracle GraalVM / Oracle Labs
         * Alibaba (Dragonwell), SAP SE (SapMachine), Tencent (Kona), JetBrains s.r.o. (JetBrains Runtime)
         * occasionally N/A or an empty parenthetical from bare OpenJDK builds
         */
    }

    /**
     * Adapted from https://github.com/gradle/gradle/blob/c29deea7d8cd7625ee4c33155e9d404a981f2a67/platforms/jvm/jvm-services/src/main/java/org/gradle/internal/jvm/inspection/JvmVendor.java#L23
     */
    enum KnownJvmVendor {
        TEMURIN("temurin", "temurin|adoptium|eclipse foundation", "Eclipse Temurin", "Temurin"),
        ADOPTOPENJDK("adoptopenjdk", "aoj|adoptopenjdk", "AdoptOpenJDK", "AOJ"),
        AMAZON("amazon", "amazon|corretto", "Amazon Corretto", "Corretto"),
        ALIBABA("alibaba", "alibaba|dragonwell", "Alibaba", "Dragonwell"),
        APPLE("apple", "Apple", null),
        AZUL("azul systems", "azul|zulu", "Azul Zulu", "Zulu"),
        BELLSOFT("bellsoft", "bellsoft|liberica", "BellSoft Liberica", "Liberica"),
        GRAAL_VM("graalvm community", "graalvm|graal vm", "GraalVM Community", "GraalVM Community"),
        HEWLETT_PACKARD("hewlett-packard", "hp|hewlett", "HP-UX", null),
        IBM("ibm", "ibm|semeru|international business machines corporation", "IBM", "Semeru"),
        JETBRAINS("jetbrains", "jbr|jetbrains", "JetBrains", "JetBrains"),
        MICROSOFT("microsoft", "Microsoft", "Microsoft"),
        ORACLE("oracle", "Oracle", "Oracle OpenJDK"),
        RED_HAT("red hat", "red ?hat", "Red Hat", "Red Hat"),
        SAP("sap se", "sap", "SAP SapMachine", "SAP Machine"),
        TENCENT("tencent", "tencent|kona", "Tencent", "Kona"),
        UNKNOWN("unknown", "unknown", null);

        private final String indicatorString;
        private final Pattern indicatorPattern;
        private final String displayName;
        private final String foojayDistroName;

        KnownJvmVendor(String indicatorString, String displayName, String foojayDistroName) {
            this.indicatorString = indicatorString;
            this.indicatorPattern = Pattern.compile(indicatorString, Pattern.CASE_INSENSITIVE);
            this.displayName = displayName;
            this.foojayDistroName = foojayDistroName;
        }

        KnownJvmVendor(String indicatorString, String pattern, String displayName, String foojayDistroName) {
            this.indicatorString = indicatorString;
            this.indicatorPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            this.displayName = displayName;
            this.foojayDistroName = foojayDistroName;
        }

        static KnownJvmVendor of(String rawVendor) {
            if (rawVendor == null) {
                return UNKNOWN;
            }
            for (KnownJvmVendor jvmVendor : KnownJvmVendor.values()) {
                if (jvmVendor.name().equals(rawVendor)) {
                    return jvmVendor;
                }
                if (jvmVendor.indicatorString.equals(rawVendor)) {
                    return jvmVendor;
                }
                if (jvmVendor.indicatorPattern.matcher(rawVendor).find()) {
                    return jvmVendor;
                }
            }
            return UNKNOWN;
        }

        public String foojayDistroName() {
            return foojayDistroName;
        }
    }
}
