package de.travelmate.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "wikidata-query")
@Produces(MediaType.APPLICATION_JSON)
public interface WikidataQueryClient {
    @GET
    @Path("/sparql")
    JsonNode query(
        @QueryParam("query") String query,
        @QueryParam("format") String format,
        @HeaderParam("User-Agent") String userAgent
    );
}
