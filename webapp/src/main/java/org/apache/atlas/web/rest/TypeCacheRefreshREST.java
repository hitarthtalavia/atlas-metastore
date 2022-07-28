package org.apache.atlas.web.rest;

import org.apache.atlas.annotation.Timed;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.repository.RepositoryException;
import org.apache.atlas.repository.graph.IAtlasGraphProvider;
import org.apache.atlas.store.AtlasTypeDefStore;
import org.apache.atlas.web.util.Servlets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import static org.apache.atlas.repository.Constants.VERTEX_INDEX;


@Path("admin/types")
@Singleton
@Service
@Consumes({Servlets.JSON_MEDIA_TYPE, MediaType.APPLICATION_JSON})
@Produces({Servlets.JSON_MEDIA_TYPE, MediaType.APPLICATION_JSON})
public class TypeCacheRefreshREST {
    private static final Logger LOG = LoggerFactory.getLogger(TypeCacheRefreshREST.class);

    private final AtlasTypeDefStore typeDefStore;
    private final IAtlasGraphProvider provider;

    @Inject
    public TypeCacheRefreshREST(AtlasTypeDefStore typeDefStore, IAtlasGraphProvider provider) {
        this.typeDefStore = typeDefStore;
        this.provider = provider;
    }

    /**
     * API to refresh type-def cache.
     *
     * @throws AtlasBaseException
     * @HTTP 204 if type def cache is refreshed successfully
     * @HTTP 500 if there is an error refreshing type def cache
     */
    @POST
    @Path("/refresh")
    @Timed
    public void refreshCache(@QueryParam("expectedFieldKeys") int expectedFieldKeys) throws AtlasBaseException, RepositoryException, InterruptedException {
        LOG.info("Initiating type-def cache refresh with expectedFieldKeys = {}", expectedFieldKeys);
        int currentSize = provider.get().getManagementSystem().getGraphIndex(VERTEX_INDEX).getFieldKeys().size();
        LOG.info("Size of field keys before refresh = {}", currentSize);

        long totalWaitTimeInMillis = 10 * 1000;//10 seconds
        long sleepTimeInMillis = 500;
        long totalIterations = Math.floorDiv(totalWaitTimeInMillis, sleepTimeInMillis);
        int counter = 0;

        while (currentSize != expectedFieldKeys && counter++ < totalIterations) {
            currentSize = provider.get().getManagementSystem().getGraphIndex(VERTEX_INDEX).getFieldKeys().size();
            LOG.info("Size found = {} at iteration {}", currentSize, counter);
            Thread.sleep(sleepTimeInMillis);
        }
        //Reload in-memory cache of type-registry
        typeDefStore.init();

        LOG.info("Size of field keys after refresh = {}", provider.get().getManagementSystem().getGraphIndex(VERTEX_INDEX).getFieldKeys().size());
        LOG.info("Completed type-def cache refresh");
    }
}
