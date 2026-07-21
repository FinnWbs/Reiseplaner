package de.travelmate.trip;

import de.travelmate.catalog.AttractionCatalogResponse;
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

    @POST
    @Path("/{id}/fill-missing-plan")
    public TripDto fillMissingPlan(@PathParam("id") Long id, GeneratePlanRequest request) {
        return trips.fillMissingPlan(id, request);
    }

    @POST
    @Path("/{id}/interests")
    public TripDto addInterest(@PathParam("id") Long id, AddTripInterestRequest request) {
        return trips.addInterest(id, request);
    }

    @DELETE
    @Path("/{id}/days/{dayId}/activities/{itemId}")
    public TripDto deleteActivity(@PathParam("id") Long id, @PathParam("dayId") Long dayId, @PathParam("itemId") Long itemId) {
        return trips.deleteActivity(id, dayId, itemId);
    }

    @PUT
    @Path("/{id}/days/{dayId}/availability")
    public TripDto updateAvailability(
        @PathParam("id") Long id,
        @PathParam("dayId") Long dayId,
        @Valid UpdateDayAvailabilityRequest request
    ) {
        return trips.updateAvailability(id, dayId, request);
    }

    @PUT
    @Path("/{id}/schedule")
    public TripDto updateSchedule(@PathParam("id") Long id, @Valid UpdateScheduleRequest request) {
        return trips.updateSchedule(id, request);
    }

    @POST
    @Path("/{id}/days/{dayId}/activities/{itemId}/regenerate")
    public TripDto regenerateActivity(
        @PathParam("id") Long id,
        @PathParam("dayId") Long dayId,
        @PathParam("itemId") Long itemId,
        RegenerateActivityRequest request
    ) {
        return trips.regenerateActivity(id, dayId, itemId, request);
    }

    @PUT
    @Path("/{id}/dates")
    public TripDto updateDates(@PathParam("id") Long id, @Valid UpdateTripDatesRequest request) {
        return trips.updateDates(id, request);
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

    @GET
    @Path("/{id}/catalog-attractions")
    public AttractionCatalogResponse catalogAttractions(@PathParam("id") Long id) {
        return trips.catalogAttractions(id);
    }

    @POST
    @Path("/{id}/days/{dayId}/catalog-attractions/{catalogId}")
    public TripDto addCatalogAttraction(
        @PathParam("id") Long id,
        @PathParam("dayId") Long dayId,
        @PathParam("catalogId") String catalogId
    ) {
        return trips.addCatalogAttraction(id, dayId, catalogId);
    }
}
