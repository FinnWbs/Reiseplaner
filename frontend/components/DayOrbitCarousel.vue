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
}>()

const pointerStart = ref<number | null>(null)
const stage = ref<HTMLElement | null>(null)

const setActive = (index: number) => {
  if (index < 0 || index >= props.trip.days.length || index === props.activeIndex) return
  emit('change', index)
  nextTick(() => stage.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

const cardStyle = (index: number) => {
  const offset = index - props.activeIndex
  const distance = Math.abs(offset)
  return {
    '--orbit-offset': offset,
    '--orbit-distance': distance,
    zIndex: props.trip.days.length - distance
  }
}

const onPointerDown = (event: PointerEvent) => {
  pointerStart.value = event.clientX
}

const onPointerUp = (event: PointerEvent) => {
  if (pointerStart.value == null) return
  const distance = event.clientX - pointerStart.value
  pointerStart.value = null
  if (Math.abs(distance) < 45) return
  setActive(props.activeIndex + (distance < 0 ? 1 : -1))
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'ArrowLeft') setActive(props.activeIndex - 1)
  if (event.key === 'ArrowRight') setActive(props.activeIndex + 1)
}
</script>

<template>
  <section class="day-orbit-section" @keydown="onKeydown">
    <div
      ref="stage"
      class="day-orbit-stage"
      tabindex="0"
      aria-label="Tagesansicht. Mit Pfeiltasten zwischen Tagen wechseln."
      @pointerdown="onPointerDown"
      @pointerup="onPointerUp"
      @pointercancel="pointerStart = null"
    >
      <div class="orbit-accent-band" aria-hidden="true" />
      <div
        v-for="(day, index) in trip.days"
        :key="day.id"
        class="orbit-card-position"
        :class="{ 'is-active': index === activeIndex, 'is-hidden': Math.abs(index - activeIndex) > 2 }"
        :style="cardStyle(index)"
      >
        <DayOrbitCard
          :day="day"
          :city="trip.city"
          :active="index === activeIndex"
          :deleting-activity-id="deletingActivityId"
          :regenerating-activity-id="regeneratingActivityId"
          @select="setActive(index)"
          @update-availability="$emit('updateAvailability', $event)"
          @regenerate-activity="(dayId, itemId) => $emit('regenerateActivity', dayId, itemId)"
          @remove-activity="(dayId, itemId) => $emit('removeActivity', dayId, itemId)"
        />
      </div>
    </div>

    <div class="orbit-controls">
      <button
        class="orbit-arrow"
        type="button"
        title="Vorheriger Tag"
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
        :disabled="activeIndex === trip.days.length - 1"
        @click="setActive(activeIndex + 1)"
      ><ArrowRight :size="21" /></button>
    </div>
  </section>
</template>
