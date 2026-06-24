<script setup lang="ts">
import { ArrowLeft, CalendarDays, MapPin } from 'lucide-vue-next'

const route = useRoute()
const workspace = useTripWorkspace()
const theme = usePlannerTheme()
const activeIndex = ref(0)

const tripId = computed(() => Number(route.params.id))

const setActiveIndex = async (index: number, replace = false) => {
  if (!workspace.trip.value || index < 0 || index >= workspace.trip.value.days.length) return
  activeIndex.value = index
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
}

watch(() => route.query.day, syncDayFromRoute)

onMounted(async () => {
  theme.initPlannerTheme()
  if (!Number.isFinite(tripId.value)) {
    await navigateTo('/calendar')
    return
  }
  await workspace.loadTrip(tripId.value)
  syncDayFromRoute()
})

onUnmounted(theme.cleanupPlannerTheme)
</script>

<template>
  <div class="workspace-page trip-orbit-page">
    <AppNavigation
      :user="workspace.user.value"
      :is-dark-mode="theme.isDarkMode.value"
      @logout="workspace.logout"
      @toggle-theme="theme.toggleTheme"
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
        />
      </template>

      <section v-else class="empty-journeys">
        <div><strong>Reise nicht gefunden</strong><p>Vielleicht wurde sie bereits entfernt.</p></div>
        <NuxtLink class="button-link" to="/calendar">Zum Kalender</NuxtLink>
      </section>
    </main>
  </div>
</template>
