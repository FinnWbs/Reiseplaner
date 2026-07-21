<script setup lang="ts">
import { ArrowLeft, ArrowRight, CalendarClock, CalendarDays, CalendarRange, Compass, Landmark, MapPin, MoonStar, Palette, Search, ShoppingBag, Sparkles, Trees, Utensils, Sun, Wind } from 'lucide-vue-next'
import type { TripDraft } from '~/composables/useTripDraft'
import type { LocationSuggestion } from '~/types/trip'

type RangePickerSide = 'left' | 'right'

const props = defineProps<{
  initialDraft?: TripDraft | null
  preparedRange?: boolean
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{
  complete: [draft: TripDraft]
}>()

const steps = 5
const step = ref(1)
const city = ref(props.initialDraft?.city || '')
const destinationMode = ref<'SEARCH' | 'INSPIRE'>('SEARCH')
const climate = ref<'WARM' | 'COOL' | null>(null)
const datesKnown = ref<boolean | null>(props.initialDraft?.datesKnown ?? null)
const startDate = ref(props.initialDraft?.startDate || '')
const endDate = ref(props.initialDraft?.endDate || '')
const daysCount = ref(props.initialDraft?.daysCount || 3)
const planningDates = ref<string[]>([...(props.initialDraft?.planningDates || [])])
const interestNames = ref<string[]>([...(props.initialDraft?.interestNames || [])])
const pace = ref<'RELAXED' | 'BALANCED' | 'ACTIVE'>(props.initialDraft?.pace || 'BALANCED')
const dayRhythm = ref<'EARLY' | 'BALANCED' | 'LATE'>(props.initialDraft?.dayRhythm || 'BALANCED')
const location = useLocationAutocomplete(city)
const rangeDialogOpen = ref(false)
const rangeCalendarMonth = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1, 12))
const rangeSelectionComplete = ref(Boolean(startDate.value && endDate.value))
const rangePreviewEnd = ref('')
const rangeSelectedDate = ref('')
const rangeError = ref('')
const rangePopoverStyle = ref<Record<string, string>>({})
const pickerMode = ref<'date' | 'flexible'>('date')
const flexibleMonth = ref(props.initialDraft?.preferredMonth || '')
const rangeStartSide = ref<RangePickerSide | ''>('')
const rangeEndSide = ref<RangePickerSide | ''>('')

const durationPresets = [
  { label: 'Ein Wochenende', days: 3 },
  { label: 'Eine Woche', days: 7 },
  { label: 'Zwei Wochen', days: 14 }
]

const suggestedCities: Record<'WARM' | 'COOL', LocationSuggestion[]> = {
  WARM: [
    { id: 'suggested-barcelona-es', city: 'Barcelona', country: 'Spanien', countryCode: 'ES' },
    { id: 'suggested-rome-it', city: 'Rom', country: 'Italien', countryCode: 'IT' },
    { id: 'suggested-lisbon-pt', city: 'Lissabon', country: 'Portugal', countryCode: 'PT' },
    { id: 'suggested-athens-gr', city: 'Athen', country: 'Griechenland', countryCode: 'GR' }
  ],
  COOL: [
    { id: 'suggested-berlin-de', city: 'Berlin', country: 'Deutschland', countryCode: 'DE' },
    { id: 'suggested-amsterdam-nl', city: 'Amsterdam', country: 'Niederlande', countryCode: 'NL' },
    { id: 'suggested-prague-cz', city: 'Prag', country: 'Tschechien', countryCode: 'CZ' },
    { id: 'suggested-copenhagen-dk', city: 'Kopenhagen', country: 'Dänemark', countryCode: 'DK' },
    { id: 'suggested-stockholm-se', city: 'Stockholm', country: 'Schweden', countryCode: 'SE' }
  ]
}
const interests = [
  { label: 'Kultur', icon: Palette },
  { label: 'Geschichte', icon: Landmark },
  { label: 'Natur', icon: Trees },
  { label: 'Food', icon: Utensils },
  { label: 'Shopping', icon: ShoppingBag },
  { label: 'Nightlife', icon: MoonStar }
]

const datesBetween = computed(() => {
  if (!startDate.value || !endDate.value || endDate.value < startDate.value) return []
  const result: string[] = []
  const cursor = new Date(`${startDate.value}T12:00:00`)
  const end = new Date(`${endDate.value}T12:00:00`)
  while (cursor <= end && result.length < 14) {
    result.push(cursor.toISOString().slice(0, 10))
    cursor.setDate(cursor.getDate() + 1)
  }
  return result
})

const normalizedRange = computed(() => {
  if (!startDate.value) return { start: '', end: '' }
  const end = endDate.value || startDate.value
  return startDate.value <= end
    ? { start: startDate.value, end }
    : { start: end, end: startDate.value }
})

const selectedRangeLabel = computed(() => normalizedRange.value.start
  ? `${formatDate(normalizedRange.value.start)} bis ${formatDate(normalizedRange.value.end)}`
  : '')

const rangeMonthLabel = computed(() => new Intl.DateTimeFormat('de-DE', {
  month: 'long',
  year: 'numeric'
}).format(rangeCalendarMonth.value))

const nextRangeCalendarMonth = computed(() => new Date(
  rangeCalendarMonth.value.getFullYear(),
  rangeCalendarMonth.value.getMonth() + 1,
  1,
  12
))

const nextRangeMonthLabel = computed(() => new Intl.DateTimeFormat('de-DE', {
  month: 'long',
  year: 'numeric'
}).format(nextRangeCalendarMonth.value))

const rangePopoverClass = computed(() => {
  if (pickerMode.value !== 'date') return ''
  if (rangeStartSide.value && rangeStartSide.value === rangeEndSide.value) {
    return `range-picker-popover--single-${rangeStartSide.value}`
  }
  return ''
})

const flexibleDurationLabel = computed(() =>
  durationPresets.find(preset => preset.days === daysCount.value)?.label || `${daysCount.value} Tage`
)

const flexibleMonthLabel = computed(() => {
  if (!flexibleMonth.value) return ''
  const [year, month] = flexibleMonth.value.split('-').map(Number)
  return new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric' })
    .format(new Date(year, month - 1, 1, 12))
})

const flexibleSummaryLabel = computed(() =>
  [flexibleDurationLabel.value, flexibleMonthLabel.value].filter(Boolean).join(' · ')
)

const flexibleMonths = computed(() => Array.from({ length: 6 }, (_, index) => {
  const date = new Date(rangeCalendarMonth.value.getFullYear(), rangeCalendarMonth.value.getMonth() + index, 1, 12)
  return {
    key: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`,
    month: new Intl.DateTimeFormat('de-DE', { month: 'long' }).format(date),
    year: date.getFullYear()
  }
}))

const suggestions = computed(() => {
  if (climate.value === 'WARM') return suggestedCities.WARM
  if (climate.value === 'COOL') return suggestedCities.COOL
  return []
})
const ready = computed(() => {
  if (step.value === 1) return city.value.trim().length >= 2
  if (step.value === 2) {
    return datesKnown.value
      ? Boolean(startDate.value && endDate.value && endDate.value >= startDate.value)
      : datesKnown.value === false && daysCount.value >= 1 && daysCount.value <= 14 && Boolean(flexibleMonth.value)
  }
  if (step.value === 3) return !datesKnown.value || planningDates.value.length > 0
  if (step.value === 4) return interestNames.value.length > 0
  return true
})

watch(datesBetween, (dates) => {
  planningDates.value = planningDates.value.filter(date => dates.includes(date))
  if (datesKnown.value && dates.length > 0 && planningDates.value.length === 0) {
    planningDates.value = [...dates]
  }
}, { immediate: true })

watch(rangeDialogOpen, (open) => {
  if (open) {
    window.addEventListener('keydown', handleRangeDialogKeydown)
    window.addEventListener('resize', handleRangeWindowChange)
    window.addEventListener('scroll', handleRangeWindowChange, true)
    document.addEventListener('click', closeRangeDialog)
    return
  }
  window.removeEventListener('keydown', handleRangeDialogKeydown)
  window.removeEventListener('resize', handleRangeWindowChange)
  window.removeEventListener('scroll', handleRangeWindowChange, true)
  document.removeEventListener('click', closeRangeDialog)
})

const togglePlanningDate = (date: string) => {
  planningDates.value = planningDates.value.includes(date)
    ? planningDates.value.filter(item => item !== date)
    : [...planningDates.value, date].sort()
}

const toggleInterest = (interest: string) => {
  interestNames.value = interestNames.value.includes(interest)
    ? interestNames.value.filter(item => item !== interest)
    : [...interestNames.value, interest]
}

const selectSuggestedCity = (suggestion: LocationSuggestion) => {
  location.selectLocation(suggestion)
}

const chooseClimate = (value: 'WARM' | 'COOL') => {
  climate.value = value
  city.value = ''
  location.resetLocationAutocomplete()
}

const chooseDestinationMode = (mode: 'SEARCH' | 'INSPIRE') => {
  destinationMode.value = mode
  climate.value = null
  if (mode === 'INSPIRE') {
    city.value = ''
    location.resetLocationAutocomplete()
  }
}

function datesInRange(from: string, until: string) {
  if (!from || !until) return []
  const dates: string[] = []
  const cursor = new Date(`${from}T12:00:00`)
  const end = new Date(`${until}T12:00:00`)
  while (cursor <= end && dates.length < 15) {
    dates.push(cursor.toISOString().slice(0, 10))
    cursor.setDate(cursor.getDate() + 1)
  }
  return dates
}

const positionRangePopover = () => {
  const width = Math.min(860, window.innerWidth - 32)
  const maxHeight = Math.max(280, window.innerHeight - 96)
  rangePopoverStyle.value = {
    position: 'fixed',
    top: '50%',
    left: '50%',
    width: `${width}px`,
    maxHeight: `${maxHeight}px`,
    transform: 'translate(-50%, -50%)'
  }
}

const openFixedRangeDialog = async () => {
  pickerMode.value = 'date'
  datesKnown.value = true
  rangeDialogOpen.value = true
  rangeError.value = ''
  rangeSelectionComplete.value = Boolean(startDate.value && endDate.value)
  rangeSelectedDate.value = startDate.value || ''
  rangeStartSide.value = ''
  rangeEndSide.value = ''
  rangePreviewEnd.value = ''
  const source = startDate.value || endDate.value
  if (source) {
    const date = new Date(`${source}T12:00:00`)
    rangeCalendarMonth.value = new Date(date.getFullYear(), date.getMonth(), 1, 12)
  }
  await nextTick()
  positionRangePopover()
}

const openFlexibleDurationDialog = async () => {
  pickerMode.value = 'flexible'
  datesKnown.value = false
  clearFixedRange()
  rangeDialogOpen.value = true
  const base = new Date()
  rangeCalendarMonth.value = new Date(base.getFullYear(), base.getMonth(), 1, 12)
  if (!flexibleMonth.value) flexibleMonth.value = flexibleMonths.value[0]?.key || ''
  await nextTick()
  positionRangePopover()
}

const closeRangeDialog = () => {
  rangeDialogOpen.value = false
  window.removeEventListener('keydown', handleRangeDialogKeydown)
  window.removeEventListener('resize', handleRangeWindowChange)
  window.removeEventListener('scroll', handleRangeWindowChange, true)
}

const handleRangeDialogKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') closeRangeDialog()
}

const handleRangeWindowChange = () => {
  if (rangeDialogOpen.value) positionRangePopover()
}

const moveRangeMonth = (offset: number) => {
  rangeStartSide.value = ''
  rangeEndSide.value = ''
  rangeCalendarMonth.value = new Date(
    rangeCalendarMonth.value.getFullYear(),
    rangeCalendarMonth.value.getMonth() + offset,
    1,
    12
  )
}

const isValidDialogRangeEnd = (date: string) => {
  if (!startDate.value) return false
  const from = startDate.value <= date ? startDate.value : date
  const until = startDate.value <= date ? date : startDate.value
  if (datesInRange(from, until).length > 14) {
    rangeError.value = 'Ein Reisezeitraum darf maximal 14 Tage umfassen.'
    return false
  }
  rangeError.value = ''
  return true
}

const previewDialogRangeEnd = (date: string, side?: RangePickerSide) => {
  if (rangeSelectionComplete.value || !isValidDialogRangeEnd(date)) return
  rangePreviewEnd.value = date
  if (side) rangeEndSide.value = side
}

const completeDialogRange = (date: string, side?: RangePickerSide) => {
  if (!isValidDialogRangeEnd(date)) return
  endDate.value = date
  rangePreviewEnd.value = ''
  if (side) rangeEndSide.value = side
  rangeSelectionComplete.value = true
}

const beginDialogRange = (date: string, side?: RangePickerSide) => {
  rangeError.value = ''
  startDate.value = date
  endDate.value = ''
  rangePreviewEnd.value = ''
  planningDates.value = []
  rangeStartSide.value = side || ''
  rangeEndSide.value = side || ''
  rangeSelectionComplete.value = false
}

const handleDialogRangeClick = (date: string, side: RangePickerSide) => {
  rangeSelectedDate.value = date
  if (!startDate.value || rangeSelectionComplete.value) {
    beginDialogRange(date, side)
    return
  }
  completeDialogRange(date, side)
}

const clearFixedRange = () => {
  startDate.value = ''
  endDate.value = ''
  rangePreviewEnd.value = ''
  planningDates.value = []
  rangeSelectedDate.value = ''
  rangeSelectionComplete.value = false
  rangeError.value = ''
  rangeStartSide.value = ''
  rangeEndSide.value = ''
}

const confirmFixedRange = () => {
  if (!startDate.value || !endDate.value || endDate.value < startDate.value) {
    rangeError.value = 'Bitte waehle ein Start- und Enddatum aus.'
    return
  }
  rangeSelectionComplete.value = true
  closeRangeDialog()
}

const clearFlexibleSelection = () => {
  daysCount.value = durationPresets[0]?.days || 3
  flexibleMonth.value = ''
}

const confirmFlexibleSelection = () => {
  if (daysCount.value < 1 || daysCount.value > 14 || !flexibleMonth.value) return
  closeRangeDialog()
}

const chooseFlexibleDuration = () => {
  datesKnown.value = false
  closeRangeDialog()
  clearFixedRange()
}

const selectFlexibleDuration = (days: number) => {
  daysCount.value = days
}

const next = () => {
  if (!ready.value || step.value >= steps) return
  closeRangeDialog()
  if (step.value === 2 && datesKnown.value === false) {
    step.value = 4
    return
  }
  step.value++
}

const previous = () => {
  if (step.value <= 1) return
  closeRangeDialog()
  if (step.value === 4 && datesKnown.value === false) {
    step.value = 2
    return
  }
  step.value--
}

const finish = () => emit('complete', {
  city: city.value.trim(),
  country: location.selectedLocation.value?.country,
  countryCode: location.selectedLocation.value?.countryCode,
  state: location.selectedLocation.value?.state,
  latitude: location.selectedLocation.value?.latitude,
  longitude: location.selectedLocation.value?.longitude,
  placeId: location.selectedLocation.value?.placeId,
  destinationSource: destinationMode.value === 'SEARCH' ? 'KNOWN' : 'SUGGESTED',
  preferredMonth: datesKnown.value ? undefined : flexibleMonth.value,
  datesKnown: Boolean(datesKnown.value),
  startDate: datesKnown.value ? startDate.value : '',
  endDate: datesKnown.value ? endDate.value : '',
  daysCount: datesKnown.value ? planningDates.value.length : daysCount.value,
  planningDates: datesKnown.value ? planningDates.value : [],
  interestNames: interestNames.value,
  pace: pace.value,
  dayRhythm: dayRhythm.value
})

onMounted(() => {
  if (!props.initialDraft?.city) return
  destinationMode.value = props.initialDraft.destinationSource === 'SUGGESTED' ? 'INSPIRE' : 'SEARCH'
  if (props.initialDraft.placeId || props.initialDraft.latitude != null || props.initialDraft.country) {
    location.selectedLocation.value = {
      id: props.initialDraft.placeId || `${props.initialDraft.city}-${props.initialDraft.countryCode || ''}`,
      city: props.initialDraft.city,
      country: props.initialDraft.country,
      countryCode: props.initialDraft.countryCode,
      state: props.initialDraft.state,
      latitude: props.initialDraft.latitude,
      longitude: props.initialDraft.longitude,
      placeId: props.initialDraft.placeId
    }
  }
})

onUnmounted(() => {
  location.cleanupLocationAutocomplete()
  window.removeEventListener('keydown', handleRangeDialogKeydown)
  window.removeEventListener('resize', handleRangeWindowChange)
  window.removeEventListener('scroll', handleRangeWindowChange, true)
  document.removeEventListener('click', closeRangeDialog)
})
</script>

<template>
  <section class="welcome-interview" aria-labelledby="welcome-question">
    <header class="welcome-interview-header">
      <span>Deine Reise beginnt hier</span>
      <strong>{{ step }} / {{ steps }}</strong>
    </header>
    <div class="welcome-progress"><span :style="{ width: `${step / steps * 100}%` }" /></div>
    <div v-if="preparedRange && startDate && endDate" class="prepared-range">
      <CalendarDays :size="17" />
      <span><strong>{{ formatDate(startDate) }} bis {{ formatDate(endDate) }}</strong>{{ planningDates.length }} Planungstage aus dem Kalender</span>
    </div>

    <div v-if="step === 1" class="welcome-question">
      <div class="question-icon"><MapPin :size="23" /></div>
      <h2 id="welcome-question">Wohin möchtest du reisen?</h2>
      <p>Suche gezielt nach einer Stadt oder entdecke passende Vorschläge.</p>
      <div class="destination-mode" aria-label="Art der Zielauswahl">
        <button :class="{ active: destinationMode === 'SEARCH' }" type="button" @click="chooseDestinationMode('SEARCH')">
          <Search :size="18" /><span><strong>Stadt suchen</strong><small>Ich weiß schon, wohin</small></span>
        </button>
        <button :class="{ active: destinationMode === 'INSPIRE' }" type="button" @click="chooseDestinationMode('INSPIRE')">
          <Compass :size="18" /><span><strong>Inspirieren lassen</strong><small>Zeig mir passende Ziele</small></span>
        </button>
      </div>

      <div v-if="destinationMode === 'SEARCH'" class="welcome-location-search">
        <LocationAutocomplete
          v-model:city="city"
          :suggestions="location.locationSuggestions.value"
          :selected-location="location.selectedLocation.value"
          :loading="location.locationLoading.value"
          :error="location.locationError.value"
          :dropdown-open="location.locationDropdownOpen.value"
          :highlighted-index="location.highlightedLocationIndex.value"
          :describe-location="location.describeLocation"
          @search="location.scheduleLocationSearch"
          @focus-dropdown="location.locationDropdownOpen.value = location.locationSuggestions.value.length > 0"
          @keydown="location.handleLocationKeydown"
          @select="location.selectLocation"
          @highlight="location.highlightedLocationIndex.value = $event"
        />
      </div>

      <div v-else class="inspiration-picker">
        <div class="climate-cards">
          <button :class="{ selected: climate === 'WARM' }" type="button" @click="chooseClimate('WARM')">
            <Sun :size="23" /><span><strong>Sonne & Wärme</strong><small>Lebendige Städte und milde Abende</small></span>
          </button>
          <button :class="{ selected: climate === 'COOL' }" type="button" @click="chooseClimate('COOL')">
            <Wind :size="23" /><span><strong>Kühl & urban</strong><small>Kultur, Architektur und klare Luft</small></span>
          </button>
        </div>
        <div v-if="climate" class="welcome-suggestions">
          <button
            v-for="suggestion in suggestions"
            :key="suggestion.id"
            :class="{ selected: location.selectedLocation.value?.id === suggestion.id }"
            type="button"
            @click="selectSuggestedCity(suggestion)"
          ><strong>{{ suggestion.city }}</strong></button>
        </div>
      </div>
    </div>

    <div v-else-if="step === 2" class="welcome-question">
      <div class="question-icon"><CalendarDays :size="23" /></div>
      <h2>Weißt du schon, wann es losgeht?</h2>
      <p>Du kannst einen festen Zeitraum wählen oder erstmal nur die Reisedauer festlegen.</p>
      <div class="welcome-choice-grid">
        <div class="welcome-range-anchor" @click.stop>
          <button :class="{ selected: datesKnown === true }" type="button" @click="openFixedRangeDialog">
            <CalendarRange :size="22" />
            <span><strong>Fester Zeitraum</strong><small v-if="startDate && endDate">{{ selectedRangeLabel }}</small></span>
          </button>
        </div>
        <div class="welcome-flexible-anchor" @click.stop>
          <button :class="{ selected: datesKnown === false }" type="button" @click="openFlexibleDurationDialog">
            <CalendarClock :size="22" />
            <span><strong>Flexible Reisedauer</strong><small v-if="datesKnown === false">{{ flexibleSummaryLabel }}</small></span>
          </button>
        </div>
      </div>
    </div>

    <div v-else-if="step === 3" class="welcome-question">
      <div class="question-icon"><CalendarDays :size="23" /></div>
      <h2>Welche Tage möchtest du verplanen?</h2>
      <p>Du kannst auch nur einzelne Tage deines Aufenthalts auswählen.</p>
      <div class="welcome-date-options">
        <button
          v-for="date in datesBetween"
          :key="date"
          :class="{ selected: planningDates.includes(date) }"
          type="button"
          @click="togglePlanningDate(date)"
        >
          <span>{{ weekdayFor(date) }}</span><strong>{{ formatDate(date) }}</strong>
        </button>
      </div>
    </div>

    <div v-else-if="step === 4" class="welcome-question">
      <div class="question-icon"><Sparkles :size="23" /></div>
      <h2>Was möchtest du erleben?</h2>
      <p>Wähle alles aus, was dich auf dieser Reise interessiert.</p>
      <div class="welcome-interests">
        <button
          v-for="interest in interests"
          :key="interest.label"
          :class="{ selected: interestNames.includes(interest.label) }"
          type="button"
          @click="toggleInterest(interest.label)"
        >
          <component :is="interest.icon" :size="17" stroke-width="2.3" aria-hidden="true" />
          <span>{{ interest.label }}</span>
        </button>
      </div>
    </div>

    <div v-else class="welcome-question">
      <div class="question-icon"><Sparkles :size="23" /></div>
      <h2>Wie soll sich {{ city }} anfühlen?</h2>
      <p>Damit passen Tagesumfang und Uhrzeiten besser zu dir.</p>
      <strong>Tempo</strong>
      <div class="welcome-segmented">
        <button :class="{ active: pace === 'RELAXED' }" type="button" @click="pace = 'RELAXED'">Entspannt</button>
        <button :class="{ active: pace === 'BALANCED' }" type="button" @click="pace = 'BALANCED'">Ausgeglichen</button>
        <button :class="{ active: pace === 'ACTIVE' }" type="button" @click="pace = 'ACTIVE'">Aktiv</button>
      </div>
      <strong>Tagesrhythmus</strong>
      <div class="welcome-segmented">
        <button :class="{ active: dayRhythm === 'EARLY' }" type="button" @click="dayRhythm = 'EARLY'">Früh</button>
        <button :class="{ active: dayRhythm === 'BALANCED' }" type="button" @click="dayRhythm = 'BALANCED'">Ausgeglichen</button>
        <button :class="{ active: dayRhythm === 'LATE' }" type="button" @click="dayRhythm = 'LATE'">Spät</button>
      </div>
    </div>
    <Teleport to="body">
      <section
        v-if="rangeDialogOpen"
        class="range-picker-popover"
        :class="[rangePopoverClass, pickerMode === 'date' ? 'range-picker-popover--date' : 'range-picker-popover--flexible']"
        :style="rangePopoverStyle"
        role="dialog"
        aria-modal="true"
        aria-label="Zeitraum auswaehlen"
        @click.stop
      >
        <template v-if="pickerMode === 'date'">
          <div class="range-picker-popover-calendar">
            <button class="date-range-arrow" type="button" aria-label="Vorherige Monate" @click="moveRangeMonth(-1)">
              <ArrowLeft :size="18" />
            </button>
            <section class="range-picker-month range-picker-month-left">
              <h3>{{ rangeMonthLabel }}</h3>
              <TravelCalendar
                :month="rangeCalendarMonth"
                :trips="[]"
                :selected-date="rangeSelectedDate"
                :range-start="startDate"
                :range-end="endDate"
                :range-preview-end="rangePreviewEnd"
                :range-complete="rangeSelectionComplete"
                @select-date="handleDialogRangeClick($event, 'left')"
                @range-hover="previewDialogRangeEnd($event, 'left')"
              />
            </section>
            <section class="range-picker-month range-picker-month-right">
              <h3>{{ nextRangeMonthLabel }}</h3>
              <TravelCalendar
                :month="nextRangeCalendarMonth"
                :trips="[]"
                :selected-date="rangeSelectedDate"
                :range-start="startDate"
                :range-end="endDate"
                :range-preview-end="rangePreviewEnd"
                :range-complete="rangeSelectionComplete"
                @select-date="handleDialogRangeClick($event, 'right')"
                @range-hover="previewDialogRangeEnd($event, 'right')"
              />
            </section>
            <button class="date-range-arrow" type="button" aria-label="Naechste Monate" @click="moveRangeMonth(1)">
              <ArrowRight :size="18" />
            </button>
          </div>

          <p v-if="rangeError" class="calendar-range-error">{{ rangeError }}</p>
          <div class="range-picker-actions">
            <button class="range-picker-clear" type="button" @click="clearFixedRange">
              Auswahl aufheben
            </button>
            <button
              class="range-picker-confirm"
              type="button"
              :disabled="!startDate || !endDate || endDate < startDate"
              @click="confirmFixedRange"
            >
              Best&auml;tigen
            </button>
          </div>
        </template>

        <div v-else class="flexible-picker-panel">
          <section class="flexible-picker-section">
            <h3>Wie lang soll dein Aufenthalt sein?</h3>
            <div class="flexible-duration-options">
              <button
                v-for="preset in durationPresets"
                :key="preset.label"
                :class="{ selected: daysCount === preset.days }"
                type="button"
                @click="selectFlexibleDuration(preset.days)"
              >{{ preset.label }}</button>
            </div>
          </section>

          <section class="flexible-picker-section">
            <h3>Wann m&ouml;chtest du reisen?</h3>
            <div class="flexible-months">
              <button
                v-for="month in flexibleMonths"
                :key="month.key"
                :class="{ selected: flexibleMonth === month.key }"
                type="button"
                @click="flexibleMonth = month.key"
              >
                <CalendarDays :size="32" />
                <strong>{{ month.month }}</strong>
                <span>{{ month.year }}</span>
              </button>
            </div>
          </section>

          <div class="range-picker-actions">
            <button class="range-picker-clear" type="button" @click="clearFlexibleSelection">
              Auswahl aufheben
            </button>
            <button
              class="range-picker-confirm"
              type="button"
              :disabled="daysCount < 1 || daysCount > 14 || !flexibleMonth"
              @click="confirmFlexibleSelection"
            >
              Best&auml;tigen
            </button>
          </div>
        </div>
      </section>
    </Teleport>
    <p v-if="error" class="error welcome-interview-error">{{ error }}</p>
    <footer class="welcome-interview-actions">
      <button class="welcome-back" type="button" :disabled="step === 1" @click="previous"><ArrowLeft :size="18" />Zurück</button>
      <button v-if="step < steps" type="button" :disabled="!ready" @click="next">Weiter<ArrowRight :size="18" /></button>
      <button v-else type="button" :disabled="loading" @click="finish">
        {{ loading ? 'Reise wird erstellt...' : 'Reise planen' }}<ArrowRight :size="18" />
      </button>
    </footer>
  </section>
</template>
