package de.travelmate.trip;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/trips")
@RolesAllowed({"USER", "ADMIN"})
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TripResource {
    @Inject
    TripService trips;

    @POST
    public TripDto create(@Valid CreateTripRequest request) {
        return trips.create(request);
    }

    @GET
    public List<TripDto> listMine() {
        return trips.listMine();
    }

    @GET
    @Path("/{id}")
    public TripDto getMine(@PathParam("id") Long id) {
        return trips.getMine(id);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        trips.delete(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/generate-plan")
    public TripDto generatePlan(@PathParam("id") Long id, GeneratePlanRequest request) {
        return trips.generatePlan(id, request);
    }

    @DELETE
    @Path("/{id}/days/{dayId}/activities/{itemId}")
    public TripDto deleteActivity(@PathParam("id") Long id, @PathParam("dayId") Long dayId, @PathParam("itemId") Long itemId) {
        return trips.deleteActivity(id, dayId, itemId);
    }

    @PUT
    @Path("/{id}/days/{dayId}/activities/{itemId}")
    public TripDto replaceActivity(
        @PathParam("id") Long id,
        @PathParam("dayId") Long dayId,
        @PathParam("itemId") Long itemId,
        @Valid ReplaceTripActivityRequest request
    ) {
        return trips.replaceActivity(id, dayId, itemId, request);
    }

    @POST
    @Path("/{id}/days/{dayId}/activities")
    public TripDto addActivity(@PathParam("id") Long id, @PathParam("dayId") Long dayId, @Valid ReplaceTripActivityRequest request) {
        return trips.addActivity(id, dayId, request);
    }
}
