package de.travelmate.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "wikipedia")
@Produces(MediaType.APPLICATION_JSON)
public interface WikipediaClient {
    @GET
    @Path("/w/api.php")
    JsonNode extract(
        @QueryParam("action") String action,
        @QueryParam("prop") String prop,
        @QueryParam("exintro") boolean intro,
        @QueryParam("explaintext") boolean plainText,
        @QueryParam("redirects") boolean redirects,
        @QueryParam("titles") String titles,
        @QueryParam("format") String format
    );
}
