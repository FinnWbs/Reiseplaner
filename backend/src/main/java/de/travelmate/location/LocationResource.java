package de.travelmate.location;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/locations")
@RolesAllowed({"USER", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
public class LocationResource {
    @Inject
    LocationService locations;

    @GET
    @Path("/autocomplete")
    public List<LocationSuggestionDto> autocomplete(@QueryParam("query") String query) {
        return locations.autocomplete(query);
    }
}
