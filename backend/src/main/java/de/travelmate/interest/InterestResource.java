package de.travelmate.interest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;

@Path("/interests")
@RolesAllowed({"USER", "ADMIN"})
public class InterestResource {
    @Inject
    InterestRepository interests;

    @GET
    public List<InterestDto> list() {
        return interests.listAll().stream().map(InterestDto::from).toList();
    }
}
