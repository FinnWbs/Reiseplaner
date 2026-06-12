package de.travelmate.user;

import de.travelmate.interest.InterestEntity;
import de.travelmate.interest.InterestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import java.util.HashSet;
import java.util.List;

@ApplicationScoped
public class UserService {
    @Inject
    CurrentUserService currentUser;

    @Inject
    InterestRepository interests;

    @Transactional
    public UserProfileDto me() {
        return UserProfileDto.from(currentUser.requireCurrentUser());
    }

    @Transactional
    public UserProfileDto updateProfile(UpdateUserProfileRequest request) {
        UserEntity user = currentUser.requireCurrentUser();
        user.displayName = request.displayName();
        return UserProfileDto.from(user);
    }

    @Transactional
    public UserProfileDto updateInterests(UpdateUserInterestsRequest request) {
        UserEntity user = currentUser.requireCurrentUser();
        List<InterestEntity> selected = request.interestIds().isEmpty()
            ? List.of()
            : interests.findByIds(request.interestIds());

        if (selected.size() != request.interestIds().size()) {
            throw new BadRequestException("Mindestens ein Interesse existiert nicht.");
        }

        user.interests = new HashSet<>(selected);
        return UserProfileDto.from(user);
    }
}
