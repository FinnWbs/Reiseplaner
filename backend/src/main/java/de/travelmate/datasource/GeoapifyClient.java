package de.travelmate.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "geoapify")
@Produces(MediaType.APPLICATION_JSON)
public interface GeoapifyClient {
    @GET
    @Path("/v1/geocode/autocomplete")
    JsonNode autocomplete(
        @QueryParam("text") String text,
        @QueryParam("type") String type,
        @QueryParam("limit") int limit,
        @QueryParam("format") String format,
        @QueryParam("lang") String language,
        @QueryParam("apiKey") String apiKey
    );

    @GET
    @Path("/v1/geocode/search")
    JsonNode geocode(
        @QueryParam("text") String text,
        @QueryParam("type") String type,
        @QueryParam("limit") int limit,
        @QueryParam("format") String format,
        @QueryParam("apiKey") String apiKey
    );

    @GET
    @Path("/v2/places")
    JsonNode places(
        @QueryParam("categories") String categories,
        @QueryParam("conditions") String conditions,
        @QueryParam("filter") String filter,
        @QueryParam("bias") String bias,
        @QueryParam("limit") int limit,
        @QueryParam("offset") int offset,
        @QueryParam("lang") String language,
        @QueryParam("apiKey") String apiKey
    );
}
