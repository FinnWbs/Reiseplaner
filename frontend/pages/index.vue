<script setup lang="ts">
import draggable from 'vuedraggable'
import {
  ArrowDownToLine,
  ArrowUpToLine,
  CalendarDays,
  Clock3,
  Dumbbell,
  GripVertical,
  Landmark,
  MapPin,
  Moon,
  Palette,
  RefreshCw,
  ShoppingBag,
  Sun,
  Star,
  Trees,
  Trash2,
  Utensils
} from 'lucide-vue-next'

type Interest = { id: number; name: string }
type TripActivity = {
  id: number
  position: number
  locked: boolean
  notes?: string
  scheduledStart: number
  durationMinutes: number
  fitsAvailability: boolean
  activity: {
    id: number
    name: string
    description?: string
    category?: string
    subcategory?: string
    address?: string
    rating?: number
    dataQualityScore: number
  }
}
type TripDay = {
  id: number
  dayNumber: number
  travelDate?: string
  weekday?: string
  availableFrom: number
  availableUntil: number
  activities: TripActivity[]
}
type Trip = {
  id: number
  city: string
  country?: string
  countryCode?: string
  state?: string
  latitude?: number
  longitude?: number
  placeId?: string
  daysCount: number
  status: string
  startDate?: string
  endDate?: string
  pace: 'RELAXED' | 'BALANCED' | 'ACTIVE'
  dayRhythm: 'EARLY' | 'BALANCED' | 'LATE'
  destinationSource: 'KNOWN' | 'SUGGESTED'
  days: TripDay[]
}
type LocationSuggestion = {
  id: string
  city: string
  country?: string
  countryCode?: string
  state?: string
  formatted?: string
  latitude?: number
  longitude?: number
  placeId?: string
}

const warmCities = ['Barcelona', 'Rom', 'Lissabon', 'Athen']
const coolCities = ['Berlin', 'Amsterdam', 'Prag', 'Kopenhagen', 'Stockholm']

const { request } = useApi()
const { clearAuth, hydrateAuth, token, user } = useAuth()
const interests = ref<Interest[]>([])
const trips = ref<Trip[]>([])
const activeTrip = ref<Trip | null>(null)
const error = ref('')
const tripError = ref('')
const loading = ref(false)
const savingSchedule = ref(false)
const deletingActivityId = ref<number | null>(null)
const regeneratingActivityId = ref<number | null>(null)
const editingDates = ref(false)
const savingDates = ref(false)
const editStartDate = ref('')
const editEndDate = ref('')
const editPlanningDates = ref<string[]>([])

const interviewStep = ref(1)
const destinationKnown = ref<boolean | null>(null)
const climate = ref<'WARM' | 'COOL'>('WARM')
const city = ref('')
const locationSuggestions = ref<LocationSuggestion[]>([])
const selectedLocation = ref<LocationSuggestion | null>(null)
const locationLoading = ref(false)
const locationError = ref('')
const locationDropdownOpen = ref(false)
const highlightedLocationIndex = ref(-1)
let locationSearchTimer: ReturnType<typeof setTimeout> | null = null
const datesKnown = ref<boolean | null>(null)
const startDate = ref('')
const endDate = ref('')
const daysCount = ref(3)
const planningDates = ref<string[]>([])
const selectedInterestIds = ref<number[]>([])
const pace = ref<'RELAXED' | 'BALANCED' | 'ACTIVE'>('BALANCED')
const dayRhythm = ref<'EARLY' | 'BALANCED' | 'LATE'>('BALANCED')
const isDarkMode = ref(false)
const isAtPageEnd = ref(false)
const pageEnd = ref<HTMLElement | null>(null)

const isLoggedIn = computed(() => Boolean(token.value))
const citySuggestions = computed(() => climate.value === 'WARM' ? warmCities : coolCities)
const dateOptions = computed(() => {
  if (!startDate.value || !endDate.value || endDate.value < startDate.value) return []
  const dates: string[] = []
  const cursor = new Date(`${startDate.value}T12:00:00`)
  const end = new Date(`${endDate.value}T12:00:00`)
  while (cursor <= end && dates.length < 31) {
    dates.push(cursor.toISOString().slice(0, 10))
    cursor.setDate(cursor.getDate() + 1)
  }
  return dates
})
const editDateOptions = computed(() => datesBetween(editStartDate.value, editEndDate.value))

const stepReady = computed(() => {
  if (interviewStep.value === 1) return destinationKnown.value !== null
  if (interviewStep.value === 2) return Boolean(city.value.trim())
  if (interviewStep.value === 3) return datesKnown.value !== null
  if (interviewStep.value === 4) {
    return datesKnown.value
      ? Boolean(startDate.value && endDate.value && endDate.value >= startDate.value)
      : daysCount.value >= 1 && daysCount.value <= 14
  }
  if (interviewStep.value === 5) {
    return !datesKnown.value || planningDates.value.length > 0
  }
  if (interviewStep.value === 6) return selectedInterestIds.value.length > 0
  return true
})

watch([startDate, endDate], () => {
  planningDates.value = planningDates.value.filter(date => dateOptions.value.includes(date))
  if (datesKnown.value && dateOptions.value.length > 0 && planningDates.value.length === 0) {
    planningDates.value = [...dateOptions.value]
  }
})

watch([editStartDate, editEndDate], () => {
  editPlanningDates.value = editPlanningDates.value.filter(date => editDateOptions.value.includes(date))
})

const loadInitialData = async () => {
  interests.value = await request<Interest[]>('/interests')
  trips.value = await request<Trip[]>('/trips')
  activeTrip.value = trips.value[0] ?? null
}

const selectSuggestedCity = (suggestion: string) => {
  city.value = suggestion
  selectedLocation.value = null
  locationSuggestions.value = []
  locationDropdownOpen.value = false
}

const describeLocation = (suggestion: LocationSuggestion) =>
  [suggestion.state, suggestion.country].filter(Boolean).join(', ')

const fetchLocationSuggestions = async () => {
  const query = city.value.trim()
  selectedLocation.value = selectedLocation.value?.city === query ? selectedLocation.value : null
  locationError.value = ''
  if (query.length < 2) {
    locationSuggestions.value = []
    locationDropdownOpen.value = false
    highlightedLocationIndex.value = -1
    return
  }
  locationLoading.value = true
  try {
    locationSuggestions.value = await request<LocationSuggestion[]>(
      `/locations/autocomplete?query=${encodeURIComponent(query)}`
    )
    locationDropdownOpen.value = locationSuggestions.value.length > 0
    highlightedLocationIndex.value = locationSuggestions.value.length > 0 ? 0 : -1
  } catch (err: any) {
    locationSuggestions.value = []
    locationDropdownOpen.value = false
    highlightedLocationIndex.value = -1
    locationError.value = errorMessage(err, 'Standortvorschlaege konnten nicht geladen werden.')
  } finally {
    locationLoading.value = false
  }
}

const scheduleLocationSearch = () => {
  selectedLocation.value = selectedLocation.value?.city === city.value.trim() ? selectedLocation.value : null
  if (locationSearchTimer) clearTimeout(locationSearchTimer)
  locationSearchTimer = setTimeout(fetchLocationSuggestions, 300)
}

const selectLocation = (suggestion: LocationSuggestion) => {
  selectedLocation.value = suggestion
  city.value = suggestion.city
  locationSuggestions.value = []
  locationDropdownOpen.value = false
  highlightedLocationIndex.value = -1
  locationError.value = ''
}

const handleLocationKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    locationDropdownOpen.value = false
    highlightedLocationIndex.value = -1
    return
  }
  if (!locationDropdownOpen.value || locationSuggestions.value.length === 0) return
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    highlightedLocationIndex.value =
      (highlightedLocationIndex.value + 1) % locationSuggestions.value.length
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    highlightedLocationIndex.value =
      (highlightedLocationIndex.value - 1 + locationSuggestions.value.length) % locationSuggestions.value.length
  } else if (event.key === 'Enter' && highlightedLocationIndex.value >= 0) {
    event.preventDefault()
    selectLocation(locationSuggestions.value[highlightedLocationIndex.value])
  }
}

const toggleInterest = (id: number) => {
  selectedInterestIds.value = selectedInterestIds.value.includes(id)
    ? selectedInterestIds.value.filter(item => item !== id)
    : [...selectedInterestIds.value, id]
}

const togglePlanningDate = (date: string) => {
  planningDates.value = planningDates.value.includes(date)
    ? planningDates.value.filter(item => item !== date)
    : [...planningDates.value, date].sort()
}

const datesBetween = (from: string, until: string) => {
  if (!from || !until || until < from) return []
  const dates: string[] = []
  const cursor = new Date(`${from}T12:00:00`)
  const end = new Date(`${until}T12:00:00`)
  while (cursor <= end && dates.length < 31) {
    dates.push(cursor.toISOString().slice(0, 10))
    cursor.setDate(cursor.getDate() + 1)
  }
  return dates
}

const beginDateEdit = () => {
  if (!activeTrip.value) return
  editStartDate.value = activeTrip.value.startDate || ''
  editEndDate.value = activeTrip.value.endDate || ''
  editPlanningDates.value = activeTrip.value.days
    .map(day => day.travelDate)
    .filter((date): date is string => Boolean(date))
  editingDates.value = true
}

const toggleEditPlanningDate = (date: string) => {
  editPlanningDates.value = editPlanningDates.value.includes(date)
    ? editPlanningDates.value.filter(item => item !== date)
    : [...editPlanningDates.value, date].sort()
}

const saveDates = async () => {
  if (!activeTrip.value || !editStartDate.value || !editEndDate.value || editPlanningDates.value.length === 0) return
  savingDates.value = true
  tripError.value = ''
  try {
    replaceTripState(await request<Trip>(`/trips/${activeTrip.value.id}/dates`, {
      method: 'PUT',
      body: {
        startDate: editStartDate.value,
        endDate: editEndDate.value,
        planningDates: editPlanningDates.value
      }
    }))
    editingDates.value = false
  } catch (err: any) {
    tripError.value = errorMessage(err, 'Reisezeitraum konnte nicht gespeichert werden.')
  } finally {
    savingDates.value = false
  }
}

const nextStep = () => {
  if (!stepReady.value || interviewStep.value >= 7) return
  interviewStep.value++
}

const previousStep = () => {
  if (interviewStep.value > 1) interviewStep.value--
}

const resetInterview = () => {
  interviewStep.value = 1
  destinationKnown.value = null
  climate.value = 'WARM'
  city.value = ''
  datesKnown.value = null
  startDate.value = ''
  endDate.value = ''
  daysCount.value = 3
  planningDates.value = []
  selectedInterestIds.value = []
  selectedLocation.value = null
  locationSuggestions.value = []
  locationDropdownOpen.value = false
  locationError.value = ''
  pace.value = 'BALANCED'
  dayRhythm.value = 'BALANCED'
}

const createTrip = async () => {
  error.value = ''
  loading.value = true
  try {
    const selectedDates = datesKnown.value ? planningDates.value : []
    const created = await request<Trip>('/trips', {
      method: 'POST',
      body: {
        city: city.value,
        daysCount: selectedDates.length || daysCount.value,
        interestIds: selectedInterestIds.value,
        startDate: datesKnown.value ? startDate.value : null,
        endDate: datesKnown.value ? endDate.value : null,
        planningDates: selectedDates,
        pace: pace.value,
        dayRhythm: dayRhythm.value,
        destinationSource: destinationKnown.value ? 'KNOWN' : 'SUGGESTED',
        country: selectedLocation.value?.country,
        countryCode: selectedLocation.value?.countryCode,
        state: selectedLocation.value?.state,
        latitude: selectedLocation.value?.latitude,
        longitude: selectedLocation.value?.longitude,
        placeId: selectedLocation.value?.placeId
      }
    })
    activeTrip.value = created
    trips.value = [created, ...trips.value.filter(trip => trip.id !== created.id)]
    resetInterview()
  } catch (err: any) {
    error.value = errorMessage(err, 'Reise konnte nicht erstellt werden.')
  } finally {
    loading.value = false
  }
}

const replaceTripState = (updated: Trip) => {
  activeTrip.value = updated
  trips.value = trips.value.map(trip => trip.id === updated.id ? updated : trip)
}

const errorMessage = (err: any, fallback: string) =>
  err?.data?.message || err?.data?.error || err?.response?._data?.message || err?.message || fallback

const isNotFoundError = (err: any) =>
  err?.statusCode === 404 || err?.response?.status === 404 || err?.status === 404

const refreshActiveTrip = async () => {
  if (!activeTrip.value) return
  try {
    const refreshed = await request<Trip>(`/trips/${activeTrip.value.id}`)
    replaceTripState(refreshed)
  } catch (err: any) {
    if (!isNotFoundError(err)) throw err
    trips.value = await request<Trip[]>('/trips')
    activeTrip.value = trips.value[0] ?? null
  }
}

const removeActivity = async (dayId: number, itemId: number) => {
  if (!activeTrip.value) return
  tripError.value = ''
  deletingActivityId.value = itemId
  try {
    replaceTripState(await request<Trip>(
      `/trips/${activeTrip.value.id}/days/${dayId}/activities/${itemId}`,
      { method: 'DELETE' }
    ))
  } catch (err: any) {
    if (isNotFoundError(err)) {
      await refreshActiveTrip()
      tripError.value = 'Der Reiseplan wurde aktualisiert. Bitte versuche es mit dem neu geladenen Plan erneut.'
    } else {
      tripError.value = errorMessage(err, 'Aktivitaet konnte nicht entfernt werden.')
    }
  } finally {
    deletingActivityId.value = null
  }
}

const regenerateActivity = async (dayId: number, itemId: number) => {
  if (!activeTrip.value) return
  tripError.value = ''
  regeneratingActivityId.value = itemId
  try {
    replaceTripState(await request<Trip>(
      `/trips/${activeTrip.value.id}/days/${dayId}/activities/${itemId}/regenerate`,
      { method: 'POST' }
    ))
  } catch (err: any) {
    if (isNotFoundError(err)) {
      await refreshActiveTrip()
      tripError.value = 'Der Reiseplan wurde aktualisiert. Bitte versuche es mit dem neu geladenen Plan erneut.'
    } else {
      tripError.value = errorMessage(err, 'Keine passende Alternative gefunden.')
    }
  } finally {
    regeneratingActivityId.value = null
  }
}

const persistSchedule = async () => {
  if (!activeTrip.value || savingSchedule.value) return
  savingSchedule.value = true
  tripError.value = ''
  try {
    replaceTripState(await request<Trip>(`/trips/${activeTrip.value.id}/schedule`, {
      method: 'PUT',
      body: {
        days: activeTrip.value.days.map(day => ({
          dayId: day.id,
          activityItemIds: day.activities.map(item => item.id)
        }))
      }
    }))
  } catch (err: any) {
    tripError.value = errorMessage(err, 'Der Zeitplan konnte nicht gespeichert werden.')
    activeTrip.value = await request<Trip>(`/trips/${activeTrip.value.id}`)
  } finally {
    savingSchedule.value = false
  }
}

const updateAvailability = async (day: TripDay) => {
  if (!activeTrip.value) return
  if (day.availableFrom >= day.availableUntil) {
    day.availableUntil = Math.min(1440, day.availableFrom + 30)
  }
  tripError.value = ''
  try {
    replaceTripState(await request<Trip>(
      `/trips/${activeTrip.value.id}/days/${day.id}/availability`,
      {
        method: 'PUT',
        body: {
          availableFrom: day.availableFrom,
          availableUntil: day.availableUntil
        }
      }
    ))
  } catch (err: any) {
    if (isNotFoundError(err)) {
      await refreshActiveTrip()
      tripError.value = 'Der Reisetag war nicht mehr aktuell. Ich habe den Plan neu geladen.'
    } else {
      tripError.value = errorMessage(err, 'Zeitfenster konnte nicht gespeichert werden.')
    }
  }
}

const deleteTrip = async (tripId: number) => {
  error.value = ''
  try {
    await request<void>(`/trips/${tripId}`, { method: 'DELETE' })
    trips.value = trips.value.filter(trip => trip.id !== tripId)
    if (activeTrip.value?.id === tripId) activeTrip.value = trips.value[0] ?? null
  } catch (err: any) {
    error.value = errorMessage(err, 'Reise konnte nicht geloescht werden.')
  }
}

const logout = async () => {
  clearAuth()
  await navigateTo('/auth')
}

const formatMinutes = (minutes: number) => {
  if (minutes === 1440) return '24:00'
  const hours = Math.floor(minutes / 60).toString().padStart(2, '0')
  const mins = (minutes % 60).toString().padStart(2, '0')
  return `${hours}:${mins}`
}

const formatDate = (date?: string) => {
  if (!date) return ''
  return new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
    .format(new Date(`${date}T12:00:00`))
}

const weekdayFor = (date: string) => new Intl.DateTimeFormat('de-DE', { weekday: 'short' })
  .format(new Date(`${date}T12:00:00`))

const categoryName = (category?: string, subcategory?: string) => {
  const value = `${category || ''} ${subcategory || ''}`.toLowerCase()
  if (/night|club|bar|pub/.test(value)) return 'Nightlife'
  if (/food|restaurant|cafe|market|catering/.test(value)) return 'Food'
  if (/park|natur|garden|forest|beach/.test(value)) return 'Natur'
  if (/shop|commercial|mall/.test(value)) return 'Shopping'
  if (/sport|stadium|fitness/.test(value)) return 'Sport'
  if (/heritage|historic|monument|castle|geschichte/.test(value)) return 'Geschichte'
  return 'Kultur'
}

const categoryIcon = (category?: string, subcategory?: string) => {
  const icons = {
    Kultur: Palette,
    Geschichte: Landmark,
    Natur: Trees,
    Food: Utensils,
    Shopping: ShoppingBag,
    Nightlife: Moon,
    Sport: Dumbbell
  }
  return icons[categoryName(category, subcategory)]
}

const applyTheme = () => {
  if (!import.meta.client) return
  document.documentElement.classList.toggle('dark-mode', isDarkMode.value)
  sessionStorage.setItem('travelmate-theme', isDarkMode.value ? 'dark' : 'light')
}

const toggleTheme = () => {
  isDarkMode.value = !isDarkMode.value
  applyTheme()
}

const updateScrollTarget = () => {
  if (!import.meta.client) return
  const maxScroll = document.documentElement.scrollHeight - window.innerHeight
  isAtPageEnd.value = window.scrollY >= maxScroll - 24
}

const scrollToPageEdge = () => {
  if (isAtPageEnd.value) {
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  pageEnd.value?.scrollIntoView({ behavior: 'smooth', block: 'end' })
}

onMounted(async () => {
  isDarkMode.value = sessionStorage.getItem('travelmate-theme') === 'dark'
  applyTheme()
  updateScrollTarget()
  window.addEventListener('scroll', updateScrollTarget, { passive: true })
  window.addEventListener('resize', updateScrollTarget)
  hydrateAuth()
  if (!token.value) {
    await navigateTo('/auth')
    return
  }
  try {
    await loadInitialData()
    nextTick(updateScrollTarget)
  } catch (err: any) {
    error.value = err?.data?.message || 'Daten konnten nicht geladen werden.'
    if (err?.statusCode === 401 || err?.response?.status === 401) {
      clearAuth()
      await navigateTo('/auth')
    }
  }
})

onUnmounted(() => {
  if (!import.meta.client) return
  if (locationSearchTimer) clearTimeout(locationSearchTimer)
  window.removeEventListener('scroll', updateScrollTarget)
  window.removeEventListener('resize', updateScrollTarget)
})
</script>

<template>
  <div class="page planner-page">
    <div class="floating-controls" aria-label="Schnellaktionen">
      <button class="floating-button" type="button" :title="isDarkMode ? 'Lightmode aktivieren' : 'Darkmode aktivieren'" @click="toggleTheme">
        <Sun v-if="isDarkMode" :size="19" />
        <Moon v-else :size="19" />
      </button>
      <button
        class="floating-button"
        type="button"
        :title="isAtPageEnd ? 'Nach oben springen' : 'Nach unten springen'"
        @click="scrollToPageEdge"
      >
        <ArrowUpToLine v-if="isAtPageEnd" :size="19" />
        <ArrowDownToLine v-else :size="19" />
      </button>
    </div>
    <main class="main">
      <section v-if="!isLoggedIn" class="panel grid">
        <h2>Anmeldung erforderlich</h2>
        <NuxtLink class="button-link" to="/auth">Zum Login</NuxtLink>
      </section>

      <template v-else>
        <section class="panel toolbar">
          <div>
            <strong>{{ user?.displayName || user?.email }}</strong>
            <p class="muted">{{ user?.email }}</p>
          </div>
          <button class="secondary" @click="logout">Abmelden</button>
        </section>

        <section class="panel interview">
          <header class="interview-header">
            <div>
              <span class="eyebrow">Neue Reise</span>
              <h2>Kurzes Reiseinterview</h2>
            </div>
            <span class="progress-label">Frage {{ interviewStep }} von 7</span>
          </header>
          <div class="progress-track"><span :style="{ width: `${interviewStep / 7 * 100}%` }" /></div>

          <div v-if="interviewStep === 1" class="question">
            <h3>Weisst du schon, wo du hinwillst?</h3>
            <div class="choice-grid">
              <button :class="{ selected: destinationKnown === true }" class="choice" @click="destinationKnown = true">Ja, Stadt eingeben</button>
              <button :class="{ selected: destinationKnown === false }" class="choice" @click="destinationKnown = false">Nein, Vorschlaege zeigen</button>
            </div>
          </div>

          <div v-else-if="interviewStep === 2" class="question">
            <template v-if="destinationKnown">
              <h3>Welche Stadt soll es sein?</h3>
              <label class="location-search">
                Reiseziel
                <div class="location-input-wrap">
                  <input
                    v-model="city"
                    type="text"
                    placeholder="z. B. Berlin"
                    autocomplete="off"
                    @input="scheduleLocationSearch"
                    @focus="locationDropdownOpen = locationSuggestions.length > 0"
                    @keydown="handleLocationKeydown"
                  >
                  <span v-if="locationLoading" class="location-loading">Sucht...</span>
                </div>
                <div v-if="locationDropdownOpen" class="location-dropdown">
                  <button
                    v-for="(suggestion, index) in locationSuggestions"
                    :key="suggestion.id"
                    type="button"
                    class="location-option"
                    :class="{ highlighted: highlightedLocationIndex === index }"
                    @mousedown.prevent="selectLocation(suggestion)"
                    @mouseenter="highlightedLocationIndex = index"
                  >
                    <MapPin :size="18" />
                    <span>
                      <strong>{{ suggestion.city }}</strong>
                      <small>{{ describeLocation(suggestion) }}</small>
                    </span>
                  </button>
                </div>
                <span v-if="selectedLocation" class="selected-location">
                  Ausgewaehlt: {{ selectedLocation.city }}<template v-if="describeLocation(selectedLocation)">, {{ describeLocation(selectedLocation) }}</template>
                </span>
                <span v-else-if="locationError" class="field-error">{{ locationError }}</span>
              </label>
            </template>
            <template v-else>
              <h3>Welches Klima passt besser?</h3>
              <div class="segmented">
                <button :class="{ active: climate === 'WARM' }" @click="climate = 'WARM'">Warm</button>
                <button :class="{ active: climate === 'COOL' }" @click="climate = 'COOL'">Kalt oder mild</button>
              </div>
              <div class="suggestions">
                <button
                  v-for="suggestion in citySuggestions"
                  :key="suggestion"
                  class="choice"
                  :class="{ selected: city === suggestion }"
                  @click="selectSuggestedCity(suggestion)"
                >{{ suggestion }}</button>
              </div>
            </template>
          </div>

          <div v-else-if="interviewStep === 3" class="question">
            <h3>Weisst du schon, wann du fahren willst?</h3>
            <div class="choice-grid">
              <button :class="{ selected: datesKnown === true }" class="choice" @click="datesKnown = true">Ja, Zeitraum waehlen</button>
              <button :class="{ selected: datesKnown === false }" class="choice" @click="datesKnown = false">Nein, nur Dauer festlegen</button>
            </div>
          </div>

          <div v-else-if="interviewStep === 4" class="question">
            <template v-if="datesKnown">
              <h3>Wann beginnt und endet deine Reise?</h3>
              <div class="columns">
                <label>Ankunft<input v-model="startDate" type="date"></label>
                <label>Abreise<input v-model="endDate" type="date" :min="startDate"></label>
              </div>
            </template>
            <template v-else>
              <h3>Wie viele Tage sollen geplant werden?</h3>
              <label>Planungstage<input v-model.number="daysCount" type="number" min="1" max="14"></label>
            </template>
          </div>

          <div v-else-if="interviewStep === 5" class="question">
            <template v-if="datesKnown">
              <h3>Welche Tage sollen wir konkret planen?</h3>
              <p class="muted">Die Planungstage duerfen eine Teilmenge deiner gesamten Reise sein.</p>
              <div class="date-grid">
                <button
                  v-for="date in dateOptions"
                  :key="date"
                  class="date-choice"
                  :class="{ selected: planningDates.includes(date) }"
                  @click="togglePlanningDate(date)"
                >
                  <span>{{ weekdayFor(date) }}</span>
                  <strong>{{ formatDate(date) }}</strong>
                </button>
              </div>
            </template>
            <template v-else>
              <h3>{{ daysCount }} Tage ohne festes Datum</h3>
              <p class="muted">Du kannst den Reisezeitraum spaeter ergaenzen. Bis dahin werden die Tage nummeriert.</p>
            </template>
          </div>

          <div v-else-if="interviewStep === 6" class="question">
            <h3>Was interessiert dich?</h3>
            <div class="interests">
              <button
                v-for="interest in interests"
                :key="interest.id"
                class="chip"
                :class="{ active: selectedInterestIds.includes(interest.id) }"
                @click="toggleInterest(interest.id)"
              >{{ interest.name }}</button>
            </div>
          </div>

          <div v-else class="question">
            <h3>Wie soll sich die Reise anfuehlen?</h3>
            <strong>Tempo</strong>
            <div class="segmented">
              <button :class="{ active: pace === 'RELAXED' }" @click="pace = 'RELAXED'">Entspannt</button>
              <button :class="{ active: pace === 'BALANCED' }" @click="pace = 'BALANCED'">Ausgeglichen</button>
              <button :class="{ active: pace === 'ACTIVE' }" @click="pace = 'ACTIVE'">Aktiv</button>
            </div>
            <strong>Tagesrhythmus</strong>
            <div class="segmented">
              <button :class="{ active: dayRhythm === 'EARLY' }" @click="dayRhythm = 'EARLY'">Frueh</button>
              <button :class="{ active: dayRhythm === 'BALANCED' }" @click="dayRhythm = 'BALANCED'">Ausgeglichen</button>
              <button :class="{ active: dayRhythm === 'LATE' }" @click="dayRhythm = 'LATE'">Spaet</button>
            </div>
            <div class="interview-summary">
              <strong>{{ city }}</strong>
              <span>{{ datesKnown ? `${planningDates.length} ausgewaehlte Planungstage` : `${daysCount} Tage` }}</span>
            </div>
          </div>

          <p v-if="error" class="error">{{ error }}</p>
          <footer class="interview-actions">
            <button class="secondary" :disabled="interviewStep === 1" @click="previousStep">Zurueck</button>
            <button v-if="interviewStep < 7" :disabled="!stepReady" @click="nextStep">Weiter</button>
            <button v-else :disabled="loading" @click="createTrip">{{ loading ? 'Plan wird erstellt...' : 'Reiseplan erstellen' }}</button>
          </footer>
        </section>

        <section v-if="activeTrip" class="panel grid">
          <div class="trip-plan-heading">
            <div>
              <span class="eyebrow">Dein Reiseplan</span>
              <h2>{{ activeTrip.city }} &middot; {{ activeTrip.daysCount }} Planungstage</h2>
              <p v-if="activeTrip.startDate" class="trip-date-range">
                <CalendarDays :size="16" />
                {{ formatDate(activeTrip.startDate) }} bis {{ formatDate(activeTrip.endDate) }}
              </p>
            </div>
            <div class="trip-plan-meta">
              <span class="muted">Zeiten sind regelbasierte Vorschlaege</span>
              <button class="secondary compact-button" @click="beginDateEdit">
                <CalendarDays :size="16" />
                {{ activeTrip.startDate ? 'Reisedaten aendern' : 'Zeitraum ergaenzen' }}
              </button>
            </div>
          </div>
          <p v-if="tripError" class="error">{{ tripError }}</p>

          <div v-if="editingDates" class="date-editor">
            <div class="columns">
              <label>Ankunft<input v-model="editStartDate" type="date"></label>
              <label>Abreise<input v-model="editEndDate" type="date" :min="editStartDate"></label>
            </div>
            <p class="muted">Waehle die Tage aus, fuer die ein konkreter Plan bestehen soll.</p>
            <div class="date-grid">
              <button
                v-for="date in editDateOptions"
                :key="date"
                class="date-choice"
                :class="{ selected: editPlanningDates.includes(date) }"
                @click="toggleEditPlanningDate(date)"
              >
                <span>{{ weekdayFor(date) }}</span>
                <strong>{{ formatDate(date) }}</strong>
              </button>
            </div>
            <div class="actions">
              <button class="secondary" @click="editingDates = false">Abbrechen</button>
              <button
                :disabled="savingDates || editPlanningDates.length === 0"
                @click="saveDates"
              >{{ savingDates ? 'Wird gespeichert...' : 'Reisedaten speichern' }}</button>
            </div>
          </div>

          <div v-for="day in activeTrip.days" :key="day.id" class="trip-day">
            <div class="trip-day-heading">
              <div class="day-number">{{ day.dayNumber }}</div>
              <div>
                <h3>{{ day.weekday || `Tag ${day.dayNumber}` }}</h3>
                <span class="muted">{{ day.travelDate ? formatDate(day.travelDate) : `${day.activities.length} Aktivitaeten` }}</span>
              </div>
            </div>

            <draggable
              v-model="day.activities"
              class="day-schedule"
              group="trip-activities"
              item-key="id"
              handle=".drag-handle"
              ghost-class="schedule-ghost"
              @end="persistSchedule"
            >
              <template #item="{ element: item }">
                <article class="schedule-item" :class="{ 'outside-window': !item.fitsAvailability }">
                  <div class="schedule-time">
                    <strong>{{ formatMinutes(item.scheduledStart) }}</strong>
                    <span>{{ item.durationMinutes }} Min.</span>
                  </div>
                  <div class="schedule-marker" aria-hidden="true"><span /></div>
                  <div class="schedule-content">
                    <button class="drag-handle" type="button" title="Aktivitaet verschieben">
                      <GripVertical :size="20" />
                    </button>
                    <div class="activity-icon" :title="categoryName(item.activity.category, item.activity.subcategory)">
                      <component :is="categoryIcon(item.activity.category, item.activity.subcategory)" :size="22" :stroke-width="1.8" />
                    </div>
                    <div class="schedule-main">
                      <span class="schedule-position">{{ categoryName(item.activity.category, item.activity.subcategory) }} &middot; Stopp {{ item.position }}</span>
                      <h4>{{ item.activity.name }}</h4>
                      <p v-if="item.activity.description" class="schedule-description">{{ item.activity.description }}</p>
                      <div class="activity-facts">
                        <span v-if="item.activity.rating != null"><Star :size="15" />{{ item.activity.rating.toFixed(1) }}</span>
                        <span v-if="item.activity.address"><MapPin :size="15" />{{ item.activity.address }}</span>
                      </div>
                      <span v-if="!item.fitsAvailability" class="window-warning">Ausserhalb deines Zeitfensters</span>
                    </div>
                    <div class="schedule-actions">
                      <button
                        type="button"
                        class="icon-button"
                        title="Alternative Aktivitaet"
                        :disabled="regeneratingActivityId === item.id"
                        @click.stop="regenerateActivity(day.id, item.id)"
                      ><RefreshCw :size="18" /></button>
                      <button
                        type="button"
                        class="icon-button"
                        title="Aktivitaet entfernen"
                        :disabled="deletingActivityId === item.id"
                        @click.stop="removeActivity(day.id, item.id)"
                      ><Trash2 :size="18" /></button>
                    </div>
                  </div>
                </article>
              </template>
            </draggable>

            <div class="availability-editor">
              <div class="availability-heading">
                <span><Clock3 :size="16" /> Freies Zeitfenster</span>
                <strong>{{ formatMinutes(day.availableFrom) }} bis {{ formatMinutes(day.availableUntil) }}</strong>
              </div>
              <div class="range-shell">
                <div class="range-track" />
                <div
                  class="range-selection"
                  :style="{
                    left: `${day.availableFrom / 1440 * 100}%`,
                    right: `${100 - day.availableUntil / 1440 * 100}%`
                  }"
                />
                <input
                  v-model.number="day.availableFrom"
                  aria-label="Beginn des Zeitfensters"
                  type="range"
                  min="0"
                  max="1410"
                  step="30"
                  @change="updateAvailability(day)"
                >
                <input
                  v-model.number="day.availableUntil"
                  aria-label="Ende des Zeitfensters"
                  type="range"
                  min="30"
                  max="1440"
                  step="30"
                  @change="updateAvailability(day)"
                >
              </div>
              <div class="range-labels"><span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>24:00</span></div>
            </div>
          </div>
        </section>

        <section v-if="trips.length > 0" class="panel grid">
          <h2>Gespeicherte Reisen</h2>
          <div class="trip-list">
            <div v-for="trip in trips" :key="trip.id" class="trip-list-item">
              <button class="secondary trip-select" @click="activeTrip = trip">
                {{ trip.city }} &middot; {{ trip.daysCount }} Tage
              </button>
              <button class="danger" @click="deleteTrip(trip.id)">Loeschen</button>
            </div>
          </div>
        </section>
      </template>
    </main>
    <div ref="pageEnd" class="page-end" aria-hidden="true" />
  </div>
</template>
