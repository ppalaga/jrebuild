package org.l2x6.jrebuild.core.build.service;

import io.foojay.api.discoclient.DiscoClient;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class FoojayDiscoService {
    private final DiscoClient discoClient;

    public FoojayDiscoService() {
        super();
        this.discoClient = new DiscoClient();
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
}
