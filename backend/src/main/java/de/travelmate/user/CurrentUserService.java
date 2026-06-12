package de.travelmate.user;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
public class CurrentUserService {
    @Inject
    JsonWebToken jwt;

    @Inject
    UserRepository users;

    public UserEntity requireCurrentUser() {
        String email = jwt.getSubject();
        if (email == null || email.isBlank()) {
            throw new NotAuthorizedException("Login erforderlich.");
        }
        return users.findByEmail(email)
            .orElseThrow(() -> new NotAuthorizedException("Login erforderlich."));
    }
}
