package de.travelmate.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "wikidata")
@Produces(MediaType.APPLICATION_JSON)
public interface WikidataClient {
    @GET
    @Path("/wiki/Special:EntityData/{id}.json")
    JsonNode entity(@PathParam("id") String id);
}
