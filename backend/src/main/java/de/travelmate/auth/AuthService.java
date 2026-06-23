package de.travelmate.auth;

import de.travelmate.user.UserEntity;
import de.travelmate.user.UserRepository;
import de.travelmate.user.UserRole;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import java.time.Duration;
import java.util.Set;

@ApplicationScoped
public class AuthService {
    @Inject
    UserRepository users;

    @Inject
    RegistrationValidator registrationValidator;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.findByEmail(request.email()).isPresent()) {
            throw new BadRequestException("E-Mail ist bereits registriert.");
        }

        UserEntity user = new UserEntity();
        user.email = request.email().toLowerCase();
        user.passwordHash = BcryptUtil.bcryptHash(request.password());
        user.displayName = registrationValidator.normalizeDisplayName(request.displayName(), request.password());
        user.role = UserRole.USER;
        users.persist(user);

        return responseFor(user);
    }

    public AuthResponse login(LoginRequest request) {
        UserEntity user = users.findByEmail(request.email())
            .orElseThrow(() -> new NotAuthorizedException("Ungueltige Zugangsdaten."));

        if (!BcryptUtil.matches(request.password(), user.passwordHash)) {
            throw new NotAuthorizedException("Ungueltige Zugangsdaten.");
        }

        return responseFor(user);
    }

    private AuthResponse responseFor(UserEntity user) {
        String token = Jwt.issuer("travelmate")
            .subject(user.email)
            .groups(Set.of(user.role.name()))
            .claim("userId", user.id)
            .expiresIn(Duration.ofHours(8))
            .sign();

        return new AuthResponse(token, user.id, user.email, user.displayName, user.role);
    }
}
