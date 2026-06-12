package de.travelmate.auth;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    @Inject
    AuthService auth;

    @POST
    @Path("/register")
    public AuthResponse register(@Valid RegisterRequest request) {
        return auth.register(request);
    }

    @POST
    @Path("/login")
    public AuthResponse login(@Valid LoginRequest request) {
        return auth.login(request);
    }
}
