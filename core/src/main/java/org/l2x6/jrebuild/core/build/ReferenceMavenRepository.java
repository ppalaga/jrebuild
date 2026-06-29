package org.l2x6.jrebuild.core.build;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.Gavtcf;

public class ReferenceMavenRepository {
    private final String referenceRepositorybaseUri;

    /** The root directory of local Maven repository, typically {@code ~/.m2/repository} */
    private final Path localMavenRepository;
    /**
     * The root directory of local reference Maven repository. This folder is private for JRebuild and
     * is used primarily for storing {@code sha1} hash files. The {@code *.pom}, {@code *-jar} atc. artifact files are
     * preferably
     * taken from {@link #localMavenRepository}.
     */
    private final Path localReferenceMavenRepository;

    public ReferenceMavenRepository(String referenceRepositorybaseUri, Path localMavenRepository,
            Path localReferenceMavenRepository) {
        super();
        this.referenceRepositorybaseUri = referenceRepositorybaseUri;
        this.localMavenRepository = localMavenRepository;
        this.localReferenceMavenRepository = localReferenceMavenRepository;
    }

    /**
     * Does the following:
     * <ol>
     * <li>Checks whether the artifacts sha1 file is available in {@link #localReferenceMavenRepository} at
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")}.
     * <li>If it is not, it downloads it from {@code referenceRepositorybaseUri +"/"+ gavtc.getRepositoryPath() + ".sha1"}
     * and stores it in {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")}.
     * <li>Checks whether {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath())} exists and whether its
     * bytes have the same
     * sha1 hash as the sha1 stored in {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")}.
     * <li>If yes, return a {@link Gavtcf} constructed from {@code gavtc} and
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath())}
     * <li>If not, checks whether {@code localMavenRepository.resolve(gavtc.getRepositoryPath())} exists and whether its
     * bytes have the same
     * sha1 hash as the sha1 stored in {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")}.
     * <li>If yes, return a {@link Gavtcf} constructed from {@code gavtc} and
     * {@code localMavenRepository.resolve(gavtc.getRepositoryPath())}
     * <li>If {@code localMavenRepository.resolve(gavtc.getRepositoryPath())} does not exist, download it from
     * {@code referenceRepositorybaseUri +"/"+ gavtc.getRepositoryPath()}
     * and update all Maven metadata related to the freshly downloaded file, as if a recent Maven 3.9.x would download it.
     * <li>If {@code localMavenRepository.resolve(gavtc.getRepositoryPath())} exists and but its bytes have a different
     * sha1 hash as the one stored in {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath() + ".sha1")},
     * then download
     * {@code referenceRepositorybaseUri +"/"+ gavtc.getRepositoryPath()} to
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath())}
     * and return a {@link Gavtcf} constructed from {@code gavtc} and
     * {@code localReferenceMavenRepository.resolve(gavtc.getRepositoryPath())}.
     * <li>Always uses Vert.x HTTP client for HTTP operations.
     *
     * @param  gavtc the {@link Gavtc} to resolve
     * @return
     */
    public Uni<Gavtcf> resolve(Gavtc gavtc) {
        return null; // FIXME
    }

}
