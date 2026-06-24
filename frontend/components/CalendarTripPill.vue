<script setup lang="ts">
import { MapPin } from 'lucide-vue-next'
import type { Trip } from '~/types/trip'

defineProps<{
  trip: Trip
  compact?: boolean
}>()

defineEmits<{
  open: [trip: Trip]
}>()

const colorIndex = (tripId: number) => Math.abs(tripId) % 6
</script>

<template>
  <button
    class="calendar-trip-pill"
    :class="[`trip-color-${colorIndex(trip.id)}`, { compact }]"
    type="button"
    :title="`${trip.city} öffnen`"
    @click.stop="$emit('open', trip)"
  >
    <MapPin v-if="!compact" :size="13" />
    <span>{{ trip.city }}</span>
  </button>
</template>
