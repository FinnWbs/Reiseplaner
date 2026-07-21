package de.travelmate.feedback;

import de.travelmate.user.CurrentUserService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/feedback")
@RolesAllowed({"USER", "ADMIN"})
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FeedbackResource {
    @Inject
    FeedbackRepository feedback;

    @Inject
    CurrentUserService currentUser;

    @POST
    @Transactional
    public FeedbackDto create(FeedbackRequest request) {
        if (request == null || request.description() == null || request.description().isBlank()) {
            throw new BadRequestException("Feedback-Text ist erforderlich.");
        }
        FeedbackEntity entity = new FeedbackEntity();
        entity.user = currentUser.requireCurrentUser();
        entity.pageUrl = blankToNull(request.pageUrl());
        entity.targetLabel = blankToNull(request.targetLabel());
        entity.targetSelector = blankToNull(request.targetSelector());
        entity.screenshotDataUrl = blankToNull(request.screenshotDataUrl());
        entity.description = request.description().trim();
        feedback.persist(entity);
        return FeedbackDto.from(entity);
    }

    @GET
    @Transactional
    public List<FeedbackDto> list() {
        return feedback.list("order by createdAt desc").stream()
            .map(FeedbackDto::from)
            .toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
