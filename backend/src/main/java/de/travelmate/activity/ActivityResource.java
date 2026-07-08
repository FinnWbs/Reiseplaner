package de.travelmate.activity;

import jakarta.annotation.security.RolesAllowed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

@Path("/activities")
@RolesAllowed({"USER", "ADMIN"})
public class ActivityResource {
    @Inject
    ActivityRepository activities;

    @Inject
    ActivityImportService importer;

    @Inject
    GooglePlacesImageService imageService;

    @GET
    public List<ActivityDto> list(@QueryParam("city") String city) {
        String normalizedCity = ActivityImportService.normalizeCity(city);
        return activities.findActiveByCity(normalizedCity).stream().map(ActivityDto::from).toList();
    }

    @GET
    @Path("/{id}")
    public ActivityDto get(@PathParam("id") Long id) {
        return activities.findByIdOptional(id)
            .map(ActivityDto::from)
            .orElseThrow(() -> new NotFoundException("Aktivitaet nicht gefunden."));
    }

    @POST
    @Path("/{id}/images")
    public List<ActivityImageDto> enrichImages(@PathParam("id") Long id) {
        return imageService.ensureImages(id);
    }

    @GET
    @PermitAll
    @Path("/{activityId}/images/{imageId}/media")
    public Response imageMedia(
        @PathParam("activityId") Long activityId,
        @PathParam("imageId") Long imageId,
        @QueryParam("maxWidthPx") Integer maxWidthPx
    ) {
        String url = imageService.resolvePhotoUri(activityId, imageId, maxWidthPx == null ? 1200 : maxWidthPx);
        return Response.temporaryRedirect(URI.create(url)).build();
    }

    @POST
    @Path("/import")
    public ActivityImportResponse importCity(@QueryParam("city") String city) {
        return importer.importCity(city);
    }
}
