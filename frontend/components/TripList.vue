<script setup lang="ts">
import type { Trip } from '~/types/trip'

defineProps<{
  trips: Trip[]
}>()

defineEmits<{
  selectTrip: [trip: Trip]
  deleteTrip: [tripId: number]
}>()
</script>

<template>
  <section v-if="trips.length > 0" class="panel grid">
    <h2>Gespeicherte Reisen</h2>
    <div class="trip-list">
      <div v-for="trip in trips" :key="trip.id" class="trip-list-item">
        <button class="secondary trip-select" @click="$emit('selectTrip', trip)">
          {{ trip.city }} &middot; {{ trip.daysCount }} Tage
        </button>
        <button class="danger" @click="$emit('deleteTrip', trip.id)">Löschen</button>
      </div>
    </div>
  </section>
</template>
