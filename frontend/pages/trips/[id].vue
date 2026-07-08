<script setup lang="ts">
import { ArrowLeft, CalendarDays, MapPin, Trash2 } from 'lucide-vue-next'

const route = useRoute()
const workspace = useTripWorkspace()
const activeIndex = ref(0)
const deleteDialogOpen = ref(false)
const dayViewError = ref('')

const tripId = computed(() => Number(route.params.id))
const activeDay = computed(() => workspace.trip.value?.days?.[activeIndex.value] || null)

const setActiveIndex = async (index: number, replace = false) => {
  if (!workspace.trip.value || index < 0 || index >= workspace.trip.value.days.length) return
  activeIndex.value = index
  workspace.preloadUpcomingImages(index)
  const day = workspace.trip.value.days[index]?.dayNumber
  await navigateTo(
    { path: route.path, query: day ? { day } : undefined },
    { replace }
  )
}

const syncDayFromRoute = () => {
  if (!workspace.trip.value) return
  const requestedDay = Number(route.query.day)
  const index = workspace.trip.value.days.findIndex(day => day.dayNumber === requestedDay)
  activeIndex.value = index >= 0 ? index : 0
  dayViewError.value = ''
  workspace.preloadUpcomingImages(activeIndex.value)
}

const removeCurrentTrip = async () => {
  const trip = workspace.trip.value
  if (!trip) return
  deleteDialogOpen.value = true
}

const confirmCurrentTripDeletion = async () => {
  const trip = workspace.trip.value
  if (!trip) return
  if (await workspace.deleteTrip(trip.id)) await navigateTo('/calendar')
}

watch(() => route.query.day, syncDayFromRoute)

onErrorCaptured((error) => {
  dayViewError.value = error instanceof Error ? error.message : 'Die Tagesansicht konnte nicht geladen werden.'
  return false
})

onMounted(async () => {
  if (!Number.isFinite(tripId.value)) {
    await navigateTo('/calendar')
    return
  }
  await workspace.loadTrip(tripId.value)
  syncDayFromRoute()
  workspace.preloadUpcomingImages(activeIndex.value)
})
</script>

<template>
  <div class="workspace-page trip-orbit-page">
    <AppNavigation
      :user="workspace.user.value"
      @logout="workspace.logout"
    />

    <main class="workspace-main orbit-page-main">
      <NuxtLink class="back-link" to="/calendar"><ArrowLeft :size="17" />Zurück zum Kalender</NuxtLink>

      <section v-if="workspace.loading.value" class="calendar-loading" aria-live="polite">
        <span />
        <p>Reise wird geladen...</p>
      </section>

      <template v-else-if="workspace.trip.value">
        <header class="orbit-page-heading">
          <div>
            <span class="eyebrow">Deine Reise</span>
            <h1>{{ workspace.trip.value.city }}</h1>
            <p>
              <MapPin :size="16" />
              {{ workspace.trip.value.country || workspace.trip.value.state || 'Städtereise' }}
              <template v-if="workspace.trip.value.startDate">
                <span>&middot;</span><CalendarDays :size="16" />
                {{ formatDate(workspace.trip.value.startDate) }} bis {{ formatDate(workspace.trip.value.endDate) }}
              </template>
            </p>
          </div>
          <div class="orbit-heading-count">
            <strong>{{ activeIndex + 1 }}</strong>
            <span>von {{ workspace.trip.value.days.length }} Tagen</span>
          </div>
        </header>

        <p v-if="workspace.error.value" class="error workspace-error">{{ workspace.error.value }}</p>

        <DayOrbitCarousel
          :trip="workspace.trip.value"
          :active-index="activeIndex"
          :deleting-activity-id="workspace.deletingActivityId.value"
          :regenerating-activity-id="workspace.regeneratingActivityId.value"
          @change="setActiveIndex"
          @update-availability="workspace.updateAvailability"
          @regenerate-activity="workspace.regenerateActivity"
          @remove-activity="workspace.removeActivity"
          @request-images="workspace.ensureActivityImages"
        />

        <section v-if="dayViewError && activeDay" class="trip-plan-fallback" aria-live="polite">
          <div>
            <span class="eyebrow">Tagesplan</span>
            <h2>Tag {{ activeDay.dayNumber }}</h2>
            <p>Die große Tagesansicht konnte gerade nicht geladen werden. Deine Stopps sind aber vorhanden.</p>
          </div>
          <ul>
            <li v-for="item in activeDay.activities" :key="item.id">
              <span>{{ formatMinutes(item.scheduledStart) }}</span>
              <strong>{{ item.activity.name }}</strong>
              <small>{{ item.activity.category || 'Aktivität' }}</small>
            </li>
          </ul>
        </section>

        <footer class="trip-danger-zone">
          <button
            class="trip-delete-button"
            type="button"
            :disabled="workspace.deletingTripId.value === workspace.trip.value.id"
            @click="removeCurrentTrip"
          ><Trash2 :size="17" />{{ workspace.deletingTripId.value ? 'Wird gelöscht...' : 'Reise löschen' }}</button>
        </footer>
      </template>

      <section v-else class="empty-journeys">
        <div><strong>Reise nicht gefunden</strong><p>Vielleicht wurde sie bereits entfernt.</p></div>
        <NuxtLink class="button-link" to="/calendar">Zum Kalender</NuxtLink>
      </section>
    </main>

    <TripDeleteDialog
      :open="deleteDialogOpen"
      :trip="workspace.trip.value"
      :loading="workspace.deletingTripId.value === workspace.trip.value?.id"
      :error="deleteDialogOpen ? workspace.error.value : ''"
      @cancel="deleteDialogOpen = false"
      @confirm="confirmCurrentTripDeletion"
    />
  </div>
</template>
