<script setup lang="ts">
import { ArrowLeft, ArrowRight } from 'lucide-vue-next'
import type { Trip, TripDay } from '~/types/trip'

const props = defineProps<{
  trip: Trip
  activeIndex: number
  deletingActivityId: number | null
  regeneratingActivityId: number | null
}>()

const emit = defineEmits<{
  change: [index: number]
  updateAvailability: [day: TripDay]
  regenerateActivity: [dayId: number, itemId: number]
  removeActivity: [dayId: number, itemId: number]
  requestImages: [activityId: number]
  openCatalog: []
}>()

const activeDay = computed(() => props.trip.days[props.activeIndex])

const setActive = (index: number) => {
  if (index < 0 || index >= props.trip.days.length || index === props.activeIndex) return
  emit('change', index)
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'ArrowLeft') setActive(props.activeIndex - 1)
  if (event.key === 'ArrowRight') setActive(props.activeIndex + 1)
}
</script>

<template>
  <section class="day-orbit-section" @keydown="onKeydown">
    <div class="day-switcher" aria-label="Reisetage">
      <button
        v-for="(day, index) in trip.days"
        :key="day.id"
        type="button"
        :class="{ active: index === activeIndex }"
        :aria-current="index === activeIndex ? 'step' : undefined"
        @click="setActive(index)"
      >
        <span>Tag {{ day.dayNumber }}</span>
        <strong>{{ day.weekday || (day.travelDate ? formatDate(day.travelDate) : `Tag ${day.dayNumber}`) }}</strong>
        <small>{{ day.activities.length }} {{ day.activities.length === 1 ? 'Stopp' : 'Stopps' }}</small>
      </button>
    </div>

    <div
      class="day-orbit-stage"
      tabindex="0"
      aria-label="Tagesansicht. Mit Pfeiltasten zwischen Tagen wechseln."
    >
      <div v-if="activeDay" class="orbit-card-position is-active">
        <DayOrbitCard
          :day="activeDay"
          :city="trip.city"
          active
          :deleting-activity-id="deletingActivityId"
          :regenerating-activity-id="regeneratingActivityId"
          @update-availability="$emit('updateAvailability', $event)"
          @regenerate-activity="(dayId, itemId) => $emit('regenerateActivity', dayId, itemId)"
          @remove-activity="(dayId, itemId) => $emit('removeActivity', dayId, itemId)"
          @request-images="$emit('requestImages', $event)"
          @open-catalog="$emit('openCatalog')"
        />
      </div>
    </div>

    <div class="orbit-controls">
      <button
        class="orbit-arrow"
        type="button"
        title="Vorheriger Tag"
        aria-label="Vorheriger Tag"
        :disabled="activeIndex === 0"
        @click="setActive(activeIndex - 1)"
      ><ArrowLeft :size="21" /></button>
      <div class="orbit-dots" aria-label="Reisetage">
        <button
          v-for="(day, index) in trip.days"
          :key="day.id"
          type="button"
          :class="{ active: index === activeIndex }"
          :aria-label="`Tag ${day.dayNumber} anzeigen`"
          @click="setActive(index)"
        />
      </div>
      <button
        class="orbit-arrow"
        type="button"
        title="Nächster Tag"
        aria-label="Nächster Tag"
        :disabled="activeIndex === trip.days.length - 1"
        @click="setActive(activeIndex + 1)"
      ><ArrowRight :size="21" /></button>
    </div>
  </section>
</template>
