<script setup lang="ts">
import type { Interest, LocationSuggestion } from '~/types/trip'

defineProps<{
  interviewStep: number
  destinationKnown: boolean | null
  climate: 'WARM' | 'COOL'
  city: string
  datesKnown: boolean | null
  startDate: string
  endDate: string
  daysCount: number
  planningDates: string[]
  selectedInterestIds: number[]
  pace: 'RELAXED' | 'BALANCED' | 'ACTIVE'
  dayRhythm: 'EARLY' | 'BALANCED' | 'LATE'
  interests: Interest[]
  citySuggestions: string[]
  dateOptions: string[]
  stepReady: boolean
  loading: boolean
  error: string
  locationSuggestions: LocationSuggestion[]
  selectedLocation: LocationSuggestion | null
  locationLoading: boolean
  locationError: string
  locationDropdownOpen: boolean
  highlightedLocationIndex: number
  describeLocation: (suggestion: LocationSuggestion) => string
  formatDate: (date?: string) => string
  weekdayFor: (date: string) => string
}>()

defineEmits<{
  updateDestinationKnown: [value: boolean]
  updateClimate: [value: 'WARM' | 'COOL']
  updateCity: [value: string]
  updateDatesKnown: [value: boolean]
  updateStartDate: [value: string]
  updateEndDate: [value: string]
  updateDaysCount: [value: number]
  updatePace: [value: 'RELAXED' | 'BALANCED' | 'ACTIVE']
  updateDayRhythm: [value: 'EARLY' | 'BALANCED' | 'LATE']
  searchLocation: []
  focusLocationDropdown: []
  locationKeydown: [event: KeyboardEvent]
  selectLocation: [suggestion: LocationSuggestion]
  highlightLocation: [index: number]
  selectSuggestedCity: [suggestion: string]
  togglePlanningDate: [date: string]
  toggleInterest: [id: number]
  previousStep: []
  nextStep: []
  createTrip: []
}>()
</script>

<template>
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
        <button :class="{ selected: destinationKnown === true }" class="choice" @click="$emit('updateDestinationKnown', true)">Ja, Stadt eingeben</button>
        <button :class="{ selected: destinationKnown === false }" class="choice" @click="$emit('updateDestinationKnown', false)">Nein, Vorschlaege zeigen</button>
      </div>
    </div>

    <div v-else-if="interviewStep === 2" class="question">
      <template v-if="destinationKnown">
        <h3>Welche Stadt soll es sein?</h3>
        <LocationAutocomplete
          :city="city"
          :suggestions="locationSuggestions"
          :selected-location="selectedLocation"
          :loading="locationLoading"
          :error="locationError"
          :dropdown-open="locationDropdownOpen"
          :highlighted-index="highlightedLocationIndex"
          :describe-location="describeLocation"
          @update:city="$emit('updateCity', $event)"
          @search="$emit('searchLocation')"
          @focus-dropdown="$emit('focusLocationDropdown')"
          @keydown="$emit('locationKeydown', $event)"
          @select="$emit('selectLocation', $event)"
          @highlight="$emit('highlightLocation', $event)"
        />
      </template>
      <template v-else>
        <h3>Welches Klima passt besser?</h3>
        <div class="segmented">
          <button :class="{ active: climate === 'WARM' }" @click="$emit('updateClimate', 'WARM')">Warm</button>
          <button :class="{ active: climate === 'COOL' }" @click="$emit('updateClimate', 'COOL')">Kalt oder mild</button>
        </div>
        <div class="suggestions">
          <button
            v-for="suggestion in citySuggestions"
            :key="suggestion"
            class="choice"
            :class="{ selected: city === suggestion }"
            @click="$emit('selectSuggestedCity', suggestion)"
          >{{ suggestion }}</button>
        </div>
      </template>
    </div>

    <div v-else-if="interviewStep === 3" class="question">
      <h3>Weisst du schon, wann du fahren willst?</h3>
      <div class="choice-grid">
        <button :class="{ selected: datesKnown === true }" class="choice" @click="$emit('updateDatesKnown', true)">Ja, Zeitraum waehlen</button>
        <button :class="{ selected: datesKnown === false }" class="choice" @click="$emit('updateDatesKnown', false)">Nein, nur Dauer festlegen</button>
      </div>
    </div>

    <div v-else-if="interviewStep === 4" class="question">
      <template v-if="datesKnown">
        <h3>Wann beginnt und endet deine Reise?</h3>
        <div class="columns">
          <label>Ankunft<input :value="startDate" type="date" @input="$emit('updateStartDate', ($event.target as HTMLInputElement).value)"></label>
          <label>Abreise<input :value="endDate" type="date" :min="startDate" @input="$emit('updateEndDate', ($event.target as HTMLInputElement).value)"></label>
        </div>
      </template>
      <template v-else>
        <h3>Wie viele Tage sollen geplant werden?</h3>
        <label>Planungstage<input :value="daysCount" type="number" min="1" max="14" @input="$emit('updateDaysCount', Number(($event.target as HTMLInputElement).value))"></label>
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
            @click="$emit('togglePlanningDate', date)"
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
          @click="$emit('toggleInterest', interest.id)"
        >{{ interest.name }}</button>
      </div>
    </div>

    <div v-else class="question">
      <h3>Wie soll sich die Reise anfuehlen?</h3>
      <strong>Tempo</strong>
      <div class="segmented">
        <button :class="{ active: pace === 'RELAXED' }" @click="$emit('updatePace', 'RELAXED')">Entspannt</button>
        <button :class="{ active: pace === 'BALANCED' }" @click="$emit('updatePace', 'BALANCED')">Ausgeglichen</button>
        <button :class="{ active: pace === 'ACTIVE' }" @click="$emit('updatePace', 'ACTIVE')">Aktiv</button>
      </div>
      <strong>Tagesrhythmus</strong>
      <div class="segmented">
        <button :class="{ active: dayRhythm === 'EARLY' }" @click="$emit('updateDayRhythm', 'EARLY')">Frueh</button>
        <button :class="{ active: dayRhythm === 'BALANCED' }" @click="$emit('updateDayRhythm', 'BALANCED')">Ausgeglichen</button>
        <button :class="{ active: dayRhythm === 'LATE' }" @click="$emit('updateDayRhythm', 'LATE')">Spaet</button>
      </div>
      <div class="interview-summary">
        <strong>{{ city }}</strong>
        <span>{{ datesKnown ? `${planningDates.length} ausgewaehlte Planungstage` : `${daysCount} Tage` }}</span>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <footer class="interview-actions">
      <button class="secondary" :disabled="interviewStep === 1" @click="$emit('previousStep')">Zurueck</button>
      <button v-if="interviewStep < 7" :disabled="!stepReady" @click="$emit('nextStep')">Weiter</button>
      <button v-else :disabled="loading" @click="$emit('createTrip')">{{ loading ? 'Plan wird erstellt...' : 'Reiseplan erstellen' }}</button>
    </footer>
  </section>
</template>
