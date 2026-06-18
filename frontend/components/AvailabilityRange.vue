<script setup lang="ts">
import { Clock3 } from 'lucide-vue-next'
import type { TripDay } from '~/types/trip'

defineProps<{
  day: TripDay
  formatMinutes: (minutes: number) => string
}>()

defineEmits<{
  update: [day: TripDay]
}>()
</script>

<template>
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
        @change="$emit('update', day)"
      >
      <input
        v-model.number="day.availableUntil"
        aria-label="Ende des Zeitfensters"
        type="range"
        min="30"
        max="1440"
        step="30"
        @change="$emit('update', day)"
      >
    </div>
    <div class="range-labels"><span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>24:00</span></div>
  </div>
</template>
