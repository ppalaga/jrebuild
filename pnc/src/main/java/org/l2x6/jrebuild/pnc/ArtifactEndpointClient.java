/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.pnc;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Set;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.response.ArtifactInfo;
import org.jboss.pnc.dto.response.Page;
import org.jboss.pnc.enums.ArtifactQuality;
import org.jboss.pnc.enums.BuildCategory;
import org.jboss.pnc.enums.RepositoryType;

@Path("/artifacts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ArtifactEndpointClient {
    static final String A_ID = "ID of the artifact";
    static final String A_PURL = "Purl of the artifact";
    static final String A_REV = "Revision number of the artifact";

    static final String GET_ALL_FILTERED_DESC = "Gets all artifacts according to specified filters.";
    static final String FILTER_IDENTIFIER_DESC = "Filter by artifact identifier or its part.";
    static final String FILTER_QUALITY_DESC = "List of artifact qualities to include in result.";
    static final String FILTER_BUILD_CATEGORY_DESC = "List of artifact build categories to include in result.";
    static final String FILTER_REPOSITORY_TYPE_DESC = "Type of target repository.";

    /**
     * {@value GET_ALL_FILTERED_DESC}
     *
     * @param  paginationParameters
     * @param  identifier           {@value FILTER_IDENTIFIER_DESC}
     * @param  qualities            {@value FILTER_QUALITY_DESC}
     * @param  repoType             {@value FILTER_REPOSITORY_TYPE_DESC}
     * @return
     */
    @GET
    @Path("/filter")
    Page<ArtifactInfo> getAllFiltered(
            @QueryParam("pageIndex") int pageIndex,
            @QueryParam("pageSize") int pageSize,
            @QueryParam("identifier") String identifier,
            @QueryParam("qualities") Set<ArtifactQuality> qualities,
            @QueryParam("repoType") RepositoryType repoType,
            @QueryParam("buildCategories") Set<BuildCategory> buildCategories);

    static final String GET_SPECIFIC_DESC = "Gets a specific artifact.";

    /**
     * {@value GET_SPECIFIC_DESC}
     *
     * @param  id {@value A_ID}
     * @return
     */
    @GET
    @Path("/{id}")
    Artifact getSpecific(@PathParam("id") String id);

}
