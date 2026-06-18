<script setup lang="ts">
const planner = useTripPlanner()
const theme = usePlannerTheme()

onMounted(async () => {
  theme.initPlannerTheme()
  await planner.initialize()
  nextTick(theme.updateScrollTarget)
})

onUnmounted(() => {
  planner.cleanupLocationAutocomplete()
  theme.cleanupPlannerTheme()
})
</script>

<template>
  <div class="page planner-page">
    <FloatingControls
      :is-dark-mode="theme.isDarkMode.value"
      :is-at-page-end="theme.isAtPageEnd.value"
      @toggle-theme="theme.toggleTheme"
      @scroll-to-edge="theme.scrollToPageEdge"
    />

    <main class="main">
      <section v-if="!planner.isLoggedIn.value" class="panel grid">
        <h2>Anmeldung erforderlich</h2>
        <NuxtLink class="button-link" to="/auth">Zum Login</NuxtLink>
      </section>

      <template v-else>
        <PlannerToolbar :user="planner.user.value" @logout="planner.logout" />

        <TripInterview
          :interview-step="planner.interviewStep.value"
          :destination-known="planner.destinationKnown.value"
          :climate="planner.climate.value"
          :city="planner.city.value"
          :dates-known="planner.datesKnown.value"
          :start-date="planner.startDate.value"
          :end-date="planner.endDate.value"
          :days-count="planner.daysCount.value"
          :planning-dates="planner.planningDates.value"
          :selected-interest-ids="planner.selectedInterestIds.value"
          :pace="planner.pace.value"
          :day-rhythm="planner.dayRhythm.value"
          :interests="planner.interests.value"
          :city-suggestions="planner.citySuggestions.value"
          :date-options="planner.dateOptions.value"
          :step-ready="planner.stepReady.value"
          :loading="planner.loading.value"
          :error="planner.error.value"
          :location-suggestions="planner.locationSuggestions.value"
          :selected-location="planner.selectedLocation.value"
          :location-loading="planner.locationLoading.value"
          :location-error="planner.locationError.value"
          :location-dropdown-open="planner.locationDropdownOpen.value"
          :highlighted-location-index="planner.highlightedLocationIndex.value"
          :describe-location="planner.describeLocation"
          :format-date="planner.formatDate"
          :weekday-for="planner.weekdayFor"
          @update-destination-known="planner.destinationKnown.value = $event"
          @update-climate="planner.climate.value = $event"
          @update-city="planner.city.value = $event"
          @update-dates-known="planner.datesKnown.value = $event"
          @update-start-date="planner.startDate.value = $event"
          @update-end-date="planner.endDate.value = $event"
          @update-days-count="planner.daysCount.value = $event"
          @update-pace="planner.pace.value = $event"
          @update-day-rhythm="planner.dayRhythm.value = $event"
          @search-location="planner.scheduleLocationSearch"
          @focus-location-dropdown="planner.locationDropdownOpen.value = planner.locationSuggestions.value.length > 0"
          @location-keydown="planner.handleLocationKeydown"
          @select-location="planner.selectLocation"
          @highlight-location="planner.highlightedLocationIndex.value = $event"
          @select-suggested-city="planner.selectSuggestedCity"
          @toggle-planning-date="planner.togglePlanningDate"
          @toggle-interest="planner.toggleInterest"
          @previous-step="planner.previousStep"
          @next-step="planner.nextStep"
          @create-trip="planner.createTrip"
        />

        <TripPlan
          v-if="planner.activeTrip.value"
          :trip="planner.activeTrip.value"
          :trip-error="planner.tripError.value"
          :editing-dates="planner.editingDates.value"
          :saving-dates="planner.savingDates.value"
          :edit-start-date="planner.editStartDate.value"
          :edit-end-date="planner.editEndDate.value"
          :edit-planning-dates="planner.editPlanningDates.value"
          :edit-date-options="planner.editDateOptions.value"
          :deleting-activity-id="planner.deletingActivityId.value"
          :regenerating-activity-id="planner.regeneratingActivityId.value"
          :format-minutes="planner.formatMinutes"
          :format-date="planner.formatDate"
          :weekday-for="planner.weekdayFor"
          @begin-date-edit="planner.beginDateEdit"
          @cancel-date-edit="planner.editingDates.value = false"
          @save-dates="planner.saveDates"
          @toggle-edit-planning-date="planner.toggleEditPlanningDate"
          @update-edit-start-date="planner.editStartDate.value = $event"
          @update-edit-end-date="planner.editEndDate.value = $event"
          @persist-schedule="planner.persistSchedule"
          @update-availability="planner.updateAvailability"
          @regenerate-activity="planner.regenerateActivity"
          @remove-activity="planner.removeActivity"
        />

        <TripList
          :trips="planner.trips.value"
          @select-trip="planner.activeTrip.value = $event"
          @delete-trip="planner.deleteTrip"
        />
      </template>
    </main>

    <div :ref="(el) => { theme.pageEnd.value = el as HTMLElement | null }" class="page-end" aria-hidden="true" />
  </div>
</template>
