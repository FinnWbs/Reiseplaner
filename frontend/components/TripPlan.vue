<script setup lang="ts">
import { CalendarDays } from 'lucide-vue-next'
import type { Trip, TripDay } from '~/types/trip'

defineProps<{
  trip: Trip
  tripError: string
  editingDates: boolean
  savingDates: boolean
  editStartDate: string
  editEndDate: string
  editPlanningDates: string[]
  editDateOptions: string[]
  deletingActivityId: number | null
  regeneratingActivityId: number | null
  formatMinutes: (minutes: number) => string
  formatDate: (date?: string) => string
  weekdayFor: (date: string) => string
}>()

const emit = defineEmits<{
  beginDateEdit: []
  cancelDateEdit: []
  saveDates: []
  toggleEditPlanningDate: [date: string]
  updateEditStartDate: [value: string]
  updateEditEndDate: [value: string]
  persistSchedule: []
  updateAvailability: [day: TripDay]
  regenerateActivity: [dayId: number, itemId: number]
  removeActivity: [dayId: number, itemId: number]
}>()
</script>

<template>
  <section class="panel grid">
    <div class="trip-plan-heading">
      <div>
        <span class="eyebrow">Dein Reiseplan</span>
        <h2>{{ trip.city }} &middot; {{ trip.daysCount }} Planungstage</h2>
        <p v-if="trip.startDate" class="trip-date-range">
          <CalendarDays :size="16" />
          {{ formatDate(trip.startDate) }} bis {{ formatDate(trip.endDate) }}
        </p>
      </div>
      <div class="trip-plan-meta">
        <span class="muted">Zeiten sind regelbasierte Vorschlaege</span>
        <button class="secondary compact-button" @click="emit('beginDateEdit')">
          <CalendarDays :size="16" />
          {{ trip.startDate ? 'Reisedaten aendern' : 'Zeitraum ergaenzen' }}
        </button>
      </div>
    </div>
    <p v-if="tripError" class="error">{{ tripError }}</p>

    <div v-if="editingDates" class="date-editor">
      <div class="columns">
        <label>
          Ankunft
          <input :value="editStartDate" type="date" @input="emit('updateEditStartDate', ($event.target as HTMLInputElement).value)">
        </label>
        <label>
          Abreise
          <input :value="editEndDate" type="date" :min="editStartDate" @input="emit('updateEditEndDate', ($event.target as HTMLInputElement).value)">
        </label>
      </div>
      <p class="muted">Waehle die Tage aus, fuer die ein konkreter Plan bestehen soll.</p>
      <div class="date-grid">
        <button
          v-for="date in editDateOptions"
          :key="date"
          class="date-choice"
          :class="{ selected: editPlanningDates.includes(date) }"
          @click="emit('toggleEditPlanningDate', date)"
        >
          <span>{{ weekdayFor(date) }}</span>
          <strong>{{ formatDate(date) }}</strong>
        </button>
      </div>
      <div class="actions">
        <button class="secondary" @click="emit('cancelDateEdit')">Abbrechen</button>
        <button
          :disabled="savingDates || editPlanningDates.length === 0"
          @click="emit('saveDates')"
        >{{ savingDates ? 'Wird gespeichert...' : 'Reisedaten speichern' }}</button>
      </div>
    </div>

    <TripDaySchedule
      v-for="day in trip.days"
      :key="day.id"
      :day="day"
      :deleting-activity-id="deletingActivityId"
      :regenerating-activity-id="regeneratingActivityId"
      :format-minutes="formatMinutes"
      :format-date="formatDate"
      @persist-schedule="emit('persistSchedule')"
      @update-availability="emit('updateAvailability', $event)"
      @regenerate-activity="(dayId, itemId) => emit('regenerateActivity', dayId, itemId)"
      @remove-activity="(dayId, itemId) => emit('removeActivity', dayId, itemId)"
    />
  </section>
</template>
