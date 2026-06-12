package de.travelmate.user;

import de.travelmate.interest.InterestDto;
import java.util.List;

public record UserProfileDto(
    Long id,
    String email,
    String displayName,
    UserRole role,
    List<InterestDto> interests
) {
    public static UserProfileDto from(UserEntity user) {
        return new UserProfileDto(
            user.id,
            user.email,
            user.displayName,
            user.role,
            user.interests.stream().map(InterestDto::from).toList()
        );
    }
}
