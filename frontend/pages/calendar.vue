<script setup lang="ts">
import { ArrowLeft, ArrowRight, CalendarCheck, CalendarPlus, ChevronDown, ChevronRight, Lightbulb, MapPin, Plus, Trash2, X } from 'lucide-vue-next'
import type { Trip } from '~/types/trip'

const workspace = useTripWorkspace()
const tripDraft = useTripDraft()
const currentMonth = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1, 12))
const selectedDate = ref('')
const rangeStart = ref('')
const rangeEnd = ref('')
const rangeComplete = ref(false)
const rangeError = ref('')
const contextMenu = ref<
  | { type: 'range'; x: number; y: number }
  | { type: 'trip'; x: number; y: number; trip: Trip }
  | null
>(null)
const plannedTripsExpanded = ref(true)
const unplannedTripsExpanded = ref(false)
const tripPendingDeletion = ref<Trip | null>(null)
const longPressTripId = ref<number | null>(null)
let longPressTimer: ReturnType<typeof setTimeout> | null = null
let longPressOrigin: { x: number; y: number } | null = null

const monthLabel = computed(() => new Intl.DateTimeFormat('de-DE', {
  month: 'long',
  year: 'numeric'
}).format(currentMonth.value))

const datedTrips = computed(() => workspace.trips.value.filter(trip => trip.startDate && trip.endDate))
const undatedTrips = computed(() => workspace.trips.value.filter(trip => !trip.startDate || !trip.endDate))
const selectedTrips = computed(() => selectedDate.value
  ? datedTrips.value.filter(trip => selectedDate.value >= trip.startDate! && selectedDate.value <= trip.endDate!)
  : [])
const normalizedRange = computed(() => {
  if (!rangeStart.value) return { start: '', end: '' }
  const end = rangeEnd.value || rangeStart.value
  return rangeStart.value <= end
    ? { start: rangeStart.value, end }
    : { start: end, end: rangeStart.value }
})
const selectedPlanningDates = computed(() => datesInRange(
  normalizedRange.value.start,
  normalizedRange.value.end
))
const selectedRangeLabel = computed(() => normalizedRange.value.start
  ? `${formatDate(normalizedRange.value.start)} bis ${formatDate(normalizedRange.value.end)}`
  : '')

function datesInRange(from: string, until: string) {
  if (!from || !until) return []
  const dates: string[] = []
  const cursor = new Date(`${from}T12:00:00`)
  const end = new Date(`${until}T12:00:00`)
  while (cursor <= end && dates.length <= 14) {
    dates.push(cursor.toISOString().slice(0, 10))
    cursor.setDate(cursor.getDate() + 1)
  }
  return dates
}

const updateRangeEnd = (date: string, complete: boolean) => {
  const from = rangeStart.value <= date ? rangeStart.value : date
  const until = rangeStart.value <= date ? date : rangeStart.value
  if (datesInRange(from, until).length > 14) {
    rangeError.value = 'Ein Reisezeitraum darf maximal 14 Tage umfassen.'
    return
  }
  rangeError.value = ''
  rangeEnd.value = date
  rangeComplete.value = complete
}

const beginRange = (date: string) => {
  contextMenu.value = null
  rangeError.value = ''
  rangeStart.value = date
  rangeEnd.value = date
  rangeComplete.value = false
}

const handleRangeClick = (date: string) => {
  selectedDate.value = date
  if (!rangeStart.value || rangeComplete.value) {
    beginRange(date)
    return
  }
  updateRangeEnd(date, true)
}

const clearRange = () => {
  rangeStart.value = ''
  rangeEnd.value = ''
  rangeComplete.value = false
  rangeError.value = ''
  contextMenu.value = null
}

const openRangeContext = (_date: string, x: number, y: number) => {
  if (rangeComplete.value) contextMenu.value = { type: 'range', x, y }
}

const createTripForRange = async () => {
  if (!rangeComplete.value || selectedPlanningDates.value.length === 0) return
  tripDraft.saveDraft({
    city: '',
    destinationSource: 'KNOWN',
    datesKnown: true,
    startDate: normalizedRange.value.start,
    endDate: normalizedRange.value.end,
    daysCount: selectedPlanningDates.value.length,
    planningDates: selectedPlanningDates.value,
    interestNames: [],
    pace: 'BALANCED',
    dayRhythm: 'BALANCED'
  })
  await navigateTo('/planner?source=calendar')
}

const moveMonth = (offset: number) => {
  currentMonth.value = new Date(
    currentMonth.value.getFullYear(),
    currentMonth.value.getMonth() + offset,
    1,
    12
  )
}

const goToday = () => {
  const today = new Date()
  currentMonth.value = new Date(today.getFullYear(), today.getMonth(), 1, 12)
  selectedDate.value = [
    today.getFullYear(),
    String(today.getMonth() + 1).padStart(2, '0'),
    String(today.getDate()).padStart(2, '0')
  ].join('-')
}

const openTrip = async (trip: Trip, date?: string) => {
  contextMenu.value = null
  const day = date ? trip.days.find(item => item.travelDate === date)?.dayNumber : undefined
  await navigateTo({ path: `/trips/${trip.id}`, query: day ? { day } : undefined })
}

const removeTrip = async (trip: Trip) => {
  contextMenu.value = null
  tripPendingDeletion.value = trip
}

const confirmTripDeletion = async () => {
  const trip = tripPendingDeletion.value
  if (!trip) return
  const deleted = await workspace.deleteTrip(trip.id)
  if (!deleted) return
  tripPendingDeletion.value = null
  if (selectedDate.value) {
    const stillHasTrip = datedTrips.value.some(item =>
      selectedDate.value >= item.startDate! && selectedDate.value <= item.endDate!
    )
    if (!stillHasTrip) selectedDate.value = ''
  }
}

const openTripContext = (event: MouseEvent, trip: Trip) => {
  event.preventDefault()
  event.stopPropagation()
  contextMenu.value = { type: 'trip', x: event.clientX, y: event.clientY, trip }
}

const clearLongPress = () => {
  if (longPressTimer) clearTimeout(longPressTimer)
  longPressTimer = null
  longPressOrigin = null
}

const startTripLongPress = (event: PointerEvent, trip: Trip) => {
  if (event.pointerType === 'mouse') return
  clearLongPress()
  longPressOrigin = { x: event.clientX, y: event.clientY }
  longPressTimer = setTimeout(() => {
    longPressTripId.value = trip.id
    contextMenu.value = { type: 'trip', x: event.clientX, y: event.clientY, trip }
    longPressTimer = null
  }, 550)
}

const moveTripLongPress = (event: PointerEvent) => {
  if (!longPressOrigin) return
  if (Math.hypot(event.clientX - longPressOrigin.x, event.clientY - longPressOrigin.y) > 10) {
    clearLongPress()
  }
}

const handleTripClick = (trip: Trip) => {
  clearLongPress()
  if (longPressTripId.value === trip.id) {
    longPressTripId.value = null
    return
  }
  openTrip(trip)
}

const closeContextMenu = () => {
  contextMenu.value = null
}

const handleEscape = (event: KeyboardEvent) => {
  if (event.key === 'Escape') closeContextMenu()
}

onMounted(async () => {
  await workspace.loadTrips()
  window.addEventListener('click', closeContextMenu)
  window.addEventListener('keydown', handleEscape)
})

onUnmounted(() => {
  clearLongPress()
  window.removeEventListener('click', closeContextMenu)
  window.removeEventListener('keydown', handleEscape)
})
</script>

<template>
  <div class="workspace-page calendar-page">
    <AppNavigation
      :user="workspace.user.value"
      @logout="workspace.logout"
    />

    <main class="workspace-main">
      <section class="calendar-hero">
        <div>
          <span class="eyebrow">Deine Reiseübersicht</span>
          <h1>Plane weniger. Erlebe mehr.</h1>
          <p>Termine, Reiseideen und Tagespläne an einem Ort.</p>
        </div>
        <NuxtLink class="button-link calendar-create-link" to="/planner"><Plus :size="18" />Neue Reise</NuxtLink>
      </section>

      <p v-if="workspace.error.value" class="error workspace-error">{{ workspace.error.value }}</p>
      <section v-if="workspace.loading.value" class="calendar-loading" aria-live="polite">
        <span />
        <p>Reisen werden geladen...</p>
      </section>

      <template v-else>
        <div class="calendar-dashboard">
        <section class="calendar-shell">
          <header class="calendar-toolbar">
            <div class="calendar-month-control">
              <button class="nav-icon-button" type="button" title="Vorheriger Monat" aria-label="Vorheriger Monat" @click="moveMonth(-1)">
                <ArrowLeft :size="19" />
              </button>
              <h2>{{ monthLabel }}</h2>
              <button class="nav-icon-button" type="button" title="Nächster Monat" aria-label="Nächster Monat" @click="moveMonth(1)">
                <ArrowRight :size="19" />
              </button>
            </div>
            <button class="secondary" type="button" @click="goToday">Heute</button>
          </header>
          <div class="calendar-hint">
            <CalendarPlus :size="16" />
            <span>Wähle einen Tag oder ziehe über mehrere Tage, um direkt eine Reise anzulegen.</span>
          </div>

          <TravelCalendar
            :month="currentMonth"
            :trips="datedTrips"
            :selected-date="selectedDate"
            :range-start="rangeStart"
            :range-end="rangeEnd"
            @select-date="handleRangeClick"
            @range-start="beginRange"
            @range-hover="updateRangeEnd($event, false)"
            @range-end="updateRangeEnd($event, true)"
            @range-context="openRangeContext"
            @open-trip="openTrip"
          />

          <div v-if="rangeComplete" class="calendar-range-action">
            <div>
              <span class="eyebrow">Ausgewählter Zeitraum</span>
              <strong>{{ selectedRangeLabel }}</strong>
              <small>{{ selectedPlanningDates.length }} {{ selectedPlanningDates.length === 1 ? 'Tag' : 'Tage' }}</small>
            </div>
            <div class="calendar-range-buttons">
              <button class="secondary compact-button" type="button" @click="clearRange"><X :size="16" />Aufheben</button>
              <button type="button" @click="createTripForRange"><Plus :size="17" />Reise anlegen</button>
            </div>
          </div>
          <p v-if="rangeError" class="calendar-range-error">{{ rangeError }}</p>

          <div v-if="selectedDate" class="mobile-selected-day">
            <strong>{{ formatDate(selectedDate) }}</strong>
            <span v-if="selectedTrips.length === 0" class="muted">Keine Reise an diesem Tag.</span>
            <CalendarTripPill
              v-for="trip in selectedTrips"
              :key="trip.id"
              :trip="trip"
              @open="openTrip(trip, selectedDate)"
            />
          </div>
        </section>

        <div
          v-if="contextMenu"
          class="calendar-context-menu"
          :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
          @click.stop
        >
          <template v-if="contextMenu.type === 'range'">
            <button type="button" @click="createTripForRange"><Plus :size="16" />Neue Reise für diesen Zeitraum</button>
            <button type="button" @click="clearRange"><X :size="16" />Auswahl aufheben</button>
          </template>
          <template v-else>
            <button type="button" @click="openTrip(contextMenu.trip)"><ChevronRight :size="16" />Reise öffnen</button>
            <button class="context-menu-danger" type="button" @click="removeTrip(contextMenu.trip)">
              <Trash2 :size="16" />Reise löschen
            </button>
          </template>
        </div>

        <aside id="reisen" class="calendar-trips-sidebar" aria-label="Reiseübersicht">
          <section class="trip-group planned-trip-group" :class="{ collapsed: !plannedTripsExpanded }">
            <button
              class="trip-group-heading"
              type="button"
              :aria-expanded="plannedTripsExpanded"
              @click="plannedTripsExpanded = !plannedTripsExpanded"
            >
              <span class="trip-group-icon"><CalendarCheck :size="19" /></span>
              <span class="trip-group-title"><span>Fest im Kalender</span><h2>Geplante Reisen</h2></span>
              <span class="trip-group-count">{{ datedTrips.length }}</span>
              <ChevronDown class="trip-group-chevron" :size="18" />
            </button>
            <div v-if="plannedTripsExpanded && datedTrips.length" class="trip-group-list">
              <button
                v-for="trip in datedTrips"
                :key="trip.id"
                class="sidebar-trip-card"
                :class="`trip-color-${Math.abs(trip.id) % 6}`"
                type="button"
                @click="handleTripClick(trip)"
                @contextmenu="openTripContext($event, trip)"
                @pointerdown="startTripLongPress($event, trip)"
                @pointermove="moveTripLongPress"
                @pointerup="clearLongPress"
                @pointercancel="clearLongPress"
                @pointerleave="clearLongPress"
              >
                <span class="journey-icon"><MapPin :size="18" /></span>
                <span class="journey-copy">
                  <strong>{{ trip.city }}</strong>
                  <small>{{ formatDate(trip.startDate) }} bis {{ formatDate(trip.endDate) }}</small>
                  <small>{{ trip.daysCount }} Planungstage</small>
                </span>
                <ChevronRight :size="18" />
              </button>
            </div>
            <div v-else-if="plannedTripsExpanded" class="trip-group-empty">
              <CalendarPlus :size="22" /><span>Noch keine Reise mit festem Zeitraum.</span>
            </div>
          </section>

          <section class="trip-group idea-trip-group" :class="{ collapsed: !unplannedTripsExpanded }">
            <button
              class="trip-group-heading"
              type="button"
              :aria-expanded="unplannedTripsExpanded"
              @click="unplannedTripsExpanded = !unplannedTripsExpanded"
            >
              <span class="trip-group-icon"><Lightbulb :size="19" /></span>
              <span class="trip-group-title"><span>Noch flexibel</span><h2>Ungeplante Reisen</h2></span>
              <span class="trip-group-count">{{ undatedTrips.length }}</span>
              <ChevronDown class="trip-group-chevron" :size="18" />
            </button>
            <div v-if="unplannedTripsExpanded && undatedTrips.length" class="trip-group-list">
              <button
                v-for="trip in undatedTrips"
                :key="trip.id"
                class="sidebar-trip-card idea"
                type="button"
                @click="handleTripClick(trip)"
                @contextmenu="openTripContext($event, trip)"
                @pointerdown="startTripLongPress($event, trip)"
                @pointermove="moveTripLongPress"
                @pointerup="clearLongPress"
                @pointercancel="clearLongPress"
                @pointerleave="clearLongPress"
              >
                <span class="journey-icon"><MapPin :size="18" /></span>
                <span class="journey-copy">
                  <strong>{{ trip.city }}</strong>
                  <small>Noch ohne Reisezeitraum</small>
                  <small>{{ trip.daysCount }} Tage vorgemerkt</small>
                </span>
                <ChevronRight :size="18" />
              </button>
            </div>
            <div v-else-if="unplannedTripsExpanded" class="trip-group-empty">
              <Lightbulb :size="22" /><span>Keine ungeplanten Reisen.</span>
            </div>
          </section>
        </aside>
        </div>
      </template>
    </main>

    <TripDeleteDialog
      :open="Boolean(tripPendingDeletion)"
      :trip="tripPendingDeletion"
      :loading="workspace.deletingTripId.value === tripPendingDeletion?.id"
      :error="tripPendingDeletion ? workspace.error.value : ''"
      @cancel="tripPendingDeletion = null"
      @confirm="confirmTripDeletion"
    />
  </div>
</template>
