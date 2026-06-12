package de.travelmate.user;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/users/me")
@RolesAllowed({"USER", "ADMIN"})
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {
    @Inject
    UserService users;

    @GET
    public UserProfileDto me() {
        return users.me();
    }

    @PUT
    public UserProfileDto updateProfile(@Valid UpdateUserProfileRequest request) {
        return users.updateProfile(request);
    }

    @PUT
    @Path("/interests")
    public UserProfileDto updateInterests(@Valid UpdateUserInterestsRequest request) {
        return users.updateInterests(request);
    }
}
