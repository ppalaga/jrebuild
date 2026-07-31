/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.mima.internal;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.extensions.mmr.MavenModelReader;
import eu.maveniverse.maven.mima.extensions.mmr.ModelRequest;
import eu.maveniverse.maven.mima.extensions.mmr.ModelResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.interpolation.StringVisitorModelInterpolator;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.jboss.logging.Logger;
import org.l2x6.pom.tuner.model.Gav;

public class CachingMavenModelReader extends MavenModelReader {

    private static final Logger log = Logger.getLogger(CachingMavenModelReader.class);

    private final Context context;
    private final Map<Gav, List<RemoteRepository>> remoteRepositories = new ConcurrentHashMap<>();
    private final Map<List<RemoteRepository>, MavenModelReaderImpl> delegates = new ConcurrentHashMap<>();

    private final Map<Gav, ModelData> cache = new ConcurrentHashMap<Gav, CachingMavenModelReader.ModelData>();

    public CachingMavenModelReader(Context context) {
        super(context);
        this.context = context;
    }

    public void register(Gav gav, List<RemoteRepository> remoteRepositories) {
        this.remoteRepositories.compute(gav, (k, v) -> {
            if (v == null || v.isEmpty()) {
                return remoteRepositories;
            } else {
                return merge(gav, v, remoteRepositories);
            }
        });
    }

    /**
     * Reads POM as {@link ModelResponse}.
     * <p>
     * Remark related to repositories: by default context "root" remote repositories will be used, unless
     * request {@link ModelRequest#getRepositories()} returns non-null value, in which case request provided
     * repositories will be used.
     */
    public ModelResponse readModel(ModelRequest request)
            throws VersionResolutionException, ArtifactResolutionException, ArtifactDescriptorException {
        Objects.requireNonNull(request, "request");
        final Artifact a = request.getArtifact();
        final Gav gav = new Gav(a.getGroupId(), a.getArtifactId(), a.getVersion());

        List<RemoteRepository> repos = remoteRepositories.get(gav);
        if (repos == null) {
            repos = context.remoteRepositories();
        }
        final InternalResult result = readInternal(gav, request, repos);
        cache.computeIfAbsent(gav, k -> result.modelData);
        return result.response;
    }

    public ModelData readModel(Gav gav) {
        return cache.computeIfAbsent(gav, k -> {
            final Artifact artifact = new DefaultArtifact(
                    gav.getGroupId(),
                    gav.getArtifactId(),
                    "",
                    "pom",
                    gav.getVersion());
            List<RemoteRepository> repos = remoteRepositories.get(gav);
            if (repos == null) {
                repos = context.remoteRepositories();
            }
            try {
                final InternalResult result = readInternal(gav, createRequest(artifact), repos);
                return result.modelData;
            } catch (VersionResolutionException | ArtifactResolutionException | ArtifactDescriptorException e) {
                throw new RuntimeException("Could not read pom of " + artifact, e);
            }

        });
    }

    public ModelData readModel(Artifact artifact) {
        final Gav gav = new Gav(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        return cache.computeIfAbsent(gav, k -> {
            List<RemoteRepository> repos = remoteRepositories.get(gav);
            if (repos == null) {
                repos = context.remoteRepositories();
            }
            try {
                final InternalResult result = readInternal(gav, createRequest(artifact), repos);
                return result.modelData;
            } catch (VersionResolutionException | ArtifactResolutionException | ArtifactDescriptorException e) {
                throw new RuntimeException("Could not read pom of " + artifact, e);
            }
        });
    }

    private ModelRequest createRequest(Artifact artifact) {
        return ModelRequest.builder().setArtifact(artifact).build();
    }

    public Model readEffectiveModel(Gav gav) {
        return readModel(gav).effectiveModel();
    }

    InternalResult readInternal(Gav gav, ModelRequest request, List<RemoteRepository> repos)
            throws VersionResolutionException, ArtifactResolutionException, ArtifactDescriptorException {
        final ModelResponse result = getDelegate(repos).readModel(context.repositorySystemSession(), request);
        final Gav parentGav = getParent(result);
        if (parentGav != null) {
            register(parentGav, repos);
        }
        return new InternalResult(result, new ModelData(
                getParent(result),
                gav,
                result.interpolateModel(result.getRawModel()),
                result.getEffectiveModel(), repos));
    }

    static record InternalResult(ModelResponse response, ModelData modelData) {
    }

    private MavenModelReaderImpl getDelegate(List<RemoteRepository> remoteRepositories) {
        return delegates.computeIfAbsent(List.copyOf(remoteRepositories), k -> new MavenModelReaderImpl(
                context.repositorySystemSession(),
                context.repositorySystem(),
                context.lookup()
                        .lookup(RemoteRepositoryManager.class)
                        .orElseThrow(() -> new IllegalStateException("RemoteRepositoryManager not available")),
                context.lookup()
                        .lookup(RepositoryEventDispatcher.class)
                        .orElseThrow(() -> new IllegalStateException("RepositoryEventDispatcher not available")),
                context.lookup()
                        .lookup(ModelBuilder.class)
                        .orElseThrow(() -> new IllegalStateException("ModelBuilder not available")),
                context.lookup()
                        .lookup(StringVisitorModelInterpolator.class)
                        .orElseThrow(() -> new IllegalStateException("StringVisitorModelInterpolator not available")),
                remoteRepositories));
    }

    static List<RemoteRepository> merge(Gav gav, List<RemoteRepository> old, List<RemoteRepository> additional) {
        if (old == additional) {
            return old;
        }
        if (old.size() == additional.size() && old.equals(additional)) {
            return old;
        }
        int i = 0;
        List<RemoteRepository> copy = null;
        for (; i < additional.size(); i++) {
            final RemoteRepository repo = additional.get(i);
            if (!old.contains(repo)) {
                if (copy == null) {
                    copy = new ArrayList<>(old);
                }
                copy.add(repo);
            }
        }
        List<RemoteRepository> result = copy != null ? Collections.unmodifiableList(copy) : old;
        return result;
    }

    static Gav getParent(ModelResponse resp) {
        Iterator<String> it = resp.getLineage().iterator();
        if (it.hasNext()) {
            /* Skip the first one which is the current pom.xml */
            it.next();

            if (it.hasNext()) {
                String id = it.next();
                if (!id.isEmpty()) {
                    /* Ignore the super pom */
                    return Gav.of(id);
                }
            }
        }
        return null;
    }

    public record ModelData(Gav parent, Gav gav, Model interpolatedModel, Model effectiveModel,
            List<RemoteRepository> repositories) {
    }

}
