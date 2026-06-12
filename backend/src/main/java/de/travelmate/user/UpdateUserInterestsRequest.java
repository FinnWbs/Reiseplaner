package de.travelmate.user;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateUserInterestsRequest(@NotNull List<Long> interestIds) {}
