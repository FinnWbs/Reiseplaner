package de.travelmate.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "wikimedia-pageviews")
@Produces(MediaType.APPLICATION_JSON)
public interface WikimediaPageviewsClient {
    @GET
    @Path("/api/rest_v1/metrics/pageviews/per-article/{project}/all-access/user/{article}/monthly/{start}/{end}")
    JsonNode monthlyPageviews(
        @PathParam("project") String project,
        @PathParam("article") String article,
        @PathParam("start") String start,
        @PathParam("end") String end,
        @HeaderParam("User-Agent") String userAgent
    );
}
