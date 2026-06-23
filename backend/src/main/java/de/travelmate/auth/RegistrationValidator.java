package de.travelmate.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

@ApplicationScoped
public class RegistrationValidator {
    public String normalizeDisplayName(String displayName, String password) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.length() < 2 || normalized.length() > 80) {
            throw new BadRequestException("Der Anzeigename muss zwischen 2 und 80 Zeichen lang sein.");
        }
        if (normalized.equals(password)) {
            throw new BadRequestException("Der Anzeigename darf nicht dem Passwort entsprechen.");
        }
        return normalized;
    }
}
