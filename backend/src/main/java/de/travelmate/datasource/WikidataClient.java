package de.travelmate.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "wikidata")
@Produces(MediaType.APPLICATION_JSON)
public interface WikidataClient {
    @GET
    @Path("/wiki/Special:EntityData/{id}.json")
    JsonNode entity(@PathParam("id") String id);

    @GET
    @Path("/w/api.php")
    JsonNode search(
        @QueryParam("action") String action,
        @QueryParam("search") String search,
        @QueryParam("language") String language,
        @QueryParam("format") String format,
        @QueryParam("limit") int limit,
        @QueryParam("type") String type,
        @HeaderParam("User-Agent") String userAgent
    );
}
