package de.travelmate.user;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserProfileRequest(@NotBlank String displayName) {}
