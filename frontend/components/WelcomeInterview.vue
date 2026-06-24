<script setup lang="ts">
import { ArrowLeft, ArrowRight, CalendarClock, CalendarDays, CalendarRange, Compass, MapPin, Search, Sparkles, Sun, Wind } from 'lucide-vue-next'
import type { TripDraft } from '~/composables/useTripDraft'
import type { LocationSuggestion } from '~/types/trip'

const props = defineProps<{
  initialDraft?: TripDraft | null
  preparedRange?: boolean
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{
  complete: [draft: TripDraft]
}>()

const steps = 6
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

const warmCities = ['Barcelona', 'Rom', 'Lissabon', 'Athen']
const coolCities = ['Berlin', 'Amsterdam', 'Prag', 'Kopenhagen', 'Stockholm']
const interests = ['Kultur', 'Geschichte', 'Natur', 'Food', 'Shopping', 'Nightlife', 'Sport']

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

const suggestions = computed(() => {
  if (climate.value === 'WARM') return warmCities
  if (climate.value === 'COOL') return coolCities
  return []
})
const ready = computed(() => {
  if (step.value === 1) return city.value.trim().length >= 2
  if (step.value === 2) return datesKnown.value !== null
  if (step.value === 3) {
    return datesKnown.value
      ? Boolean(startDate.value && endDate.value && endDate.value >= startDate.value)
      : daysCount.value >= 1 && daysCount.value <= 14
  }
  if (step.value === 4) return !datesKnown.value || planningDates.value.length > 0
  if (step.value === 5) return interestNames.value.length > 0
  return true
})

watch(datesBetween, (dates) => {
  planningDates.value = planningDates.value.filter(date => dates.includes(date))
  if (datesKnown.value && dates.length > 0 && planningDates.value.length === 0) {
    planningDates.value = [...dates]
  }
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

const selectSuggestedCity = (suggestion: string) => {
  city.value = suggestion
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

const next = () => {
  if (ready.value && step.value < steps) step.value++
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
  if (props.initialDraft.placeId || props.initialDraft.latitude != null) {
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

onUnmounted(location.cleanupLocationAutocomplete)
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
          <button :class="{ selected: climate === 'WARM' }" type="button" @click="climate = 'WARM'; city = ''">
            <Sun :size="23" /><span><strong>Sonne & Wärme</strong><small>Lebendige Städte und milde Abende</small></span>
          </button>
          <button :class="{ selected: climate === 'COOL' }" type="button" @click="climate = 'COOL'; city = ''">
            <Wind :size="23" /><span><strong>Kühl & urban</strong><small>Kultur, Architektur und klare Luft</small></span>
          </button>
        </div>
        <div v-if="climate" class="welcome-suggestions">
          <button
            v-for="suggestion in suggestions"
            :key="suggestion"
            :class="{ selected: city === suggestion }"
            type="button"
            @click="selectSuggestedCity(suggestion)"
          >{{ suggestion }}</button>
        </div>
      </div>
    </div>

    <div v-else-if="step === 2" class="welcome-question">
      <div class="question-icon"><CalendarDays :size="23" /></div>
      <h2>Weißt du schon, wann es losgeht?</h2>
      <p>Du kannst einen festen Zeitraum wählen oder erstmal nur die Reisedauer festlegen.</p>
      <div class="welcome-choice-grid">
        <button :class="{ selected: datesKnown === true }" type="button" @click="datesKnown = true">
          <CalendarRange :size="22" /><strong>Fester Zeitraum</strong>
        </button>
        <button :class="{ selected: datesKnown === false }" type="button" @click="datesKnown = false">
          <CalendarClock :size="22" /><strong>Flexible Reisedauer</strong>
        </button>
      </div>
    </div>

    <div v-else-if="step === 3" class="welcome-question">
      <div class="question-icon"><CalendarDays :size="23" /></div>
      <template v-if="datesKnown">
        <h2>Wann findet deine Reise statt?</h2>
        <div class="welcome-date-grid">
          <label>Ankunft<input v-model="startDate" type="date"></label>
          <label>Abreise<input v-model="endDate" type="date" :min="startDate"></label>
        </div>
      </template>
      <template v-else>
        <h2>Wie viele Tage sollen wir planen?</h2>
        <label class="welcome-days-input">Planungstage<input v-model.number="daysCount" type="number" min="1" max="14"></label>
      </template>
    </div>

    <div v-else-if="step === 4" class="welcome-question">
      <template v-if="datesKnown">
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
      </template>
      <template v-else>
        <div class="question-icon"><Sparkles :size="23" /></div>
        <h2>{{ daysCount }} Tage voller Möglichkeiten</h2>
        <p>Den genauen Zeitraum kannst du später jederzeit im Kalender ergänzen.</p>
      </template>
    </div>

    <div v-else-if="step === 5" class="welcome-question">
      <div class="question-icon"><Sparkles :size="23" /></div>
      <h2>Was möchtest du erleben?</h2>
      <p>Wähle alles aus, was dich auf dieser Reise interessiert.</p>
      <div class="welcome-interests">
        <button
          v-for="interest in interests"
          :key="interest"
          :class="{ selected: interestNames.includes(interest) }"
          type="button"
          @click="toggleInterest(interest)"
        >{{ interest }}</button>
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

    <p v-if="error" class="error welcome-interview-error">{{ error }}</p>
    <footer class="welcome-interview-actions">
      <button class="welcome-back" type="button" :disabled="step === 1" @click="step--"><ArrowLeft :size="18" />Zurück</button>
      <button v-if="step < steps" type="button" :disabled="!ready" @click="next">Weiter<ArrowRight :size="18" /></button>
      <button v-else type="button" :disabled="loading" @click="finish">
        {{ loading ? 'Reise wird erstellt...' : 'Reise planen' }}<ArrowRight :size="18" />
      </button>
    </footer>
  </section>
</template>
