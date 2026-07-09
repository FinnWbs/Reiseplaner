<script setup lang="ts">
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
  reorderActivities: [dayId: number, activityItemIds: number[]]
  updateActivityTiming: [dayId: number, itemId: number, scheduledStart: number, durationMinutes: number]
  moveActivityToDay: [sourceDayId: number, itemId: number, targetDayId: number]
  openCatalog: []
}>()

const activeDay = computed(() => props.trip.days[props.activeIndex])
const draggedActivity = ref<{ sourceDayId: number; itemId: number } | null>(null)
const hoveredDropDayId = ref<number | null>(null)
let clearDragTimer: ReturnType<typeof setTimeout> | null = null

const setActive = (index: number) => {
  if (index < 0 || index >= props.trip.days.length || index === props.activeIndex) return
  emit('change', index)
}

const isDropTarget = (day: TripDay) =>
  Boolean(draggedActivity.value && hoveredDropDayId.value === day.id && draggedActivity.value.sourceDayId !== day.id)

const onActivityDragStart = (dayId: number, itemId: number) => {
  if (clearDragTimer) clearTimeout(clearDragTimer)
  draggedActivity.value = { sourceDayId: dayId, itemId }
  hoveredDropDayId.value = null
}

const onActivityDragEnd = () => {
  if (clearDragTimer) clearTimeout(clearDragTimer)
  clearDragTimer = setTimeout(() => {
    draggedActivity.value = null
    hoveredDropDayId.value = null
    clearDragTimer = null
  }, 120)
}

const hoverDropDay = (day: TripDay) => {
  if (!draggedActivity.value || draggedActivity.value.sourceDayId === day.id) {
    hoveredDropDayId.value = null
    return
  }
  hoveredDropDayId.value = day.id
}

const clearHoveredDropDay = (day: TripDay) => {
  if (hoveredDropDayId.value === day.id) hoveredDropDayId.value = null
}

const dropActivityOnDay = (day: TripDay, index: number) => {
  if (!draggedActivity.value || draggedActivity.value.sourceDayId === day.id) return
  emit('moveActivityToDay', draggedActivity.value.sourceDayId, draggedActivity.value.itemId, day.id)
  setActive(index)
  draggedActivity.value = null
  hoveredDropDayId.value = null
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'ArrowLeft') setActive(props.activeIndex - 1)
  if (event.key === 'ArrowRight') setActive(props.activeIndex + 1)
}

onUnmounted(() => {
  if (clearDragTimer) clearTimeout(clearDragTimer)
  hoveredDropDayId.value = null
})
</script>

<template>
  <section class="day-orbit-section" @keydown="onKeydown">
    <div class="day-switcher" aria-label="Reisetage">
      <button
        v-for="(day, index) in trip.days"
        :key="day.id"
        type="button"
        :class="{ active: index === activeIndex, 'day-drop-target': isDropTarget(day) }"
        :aria-current="index === activeIndex ? 'step' : undefined"
        @click="setActive(index)"
        @dragover.prevent="hoverDropDay(day)"
        @dragenter.prevent="hoverDropDay(day)"
        @dragleave="clearHoveredDropDay(day)"
        @drop.prevent="dropActivityOnDay(day, index)"
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
          @reorder-activities="(dayId, activityItemIds) => $emit('reorderActivities', dayId, activityItemIds)"
          @update-activity-timing="(dayId, itemId, scheduledStart, durationMinutes) => $emit('updateActivityTiming', dayId, itemId, scheduledStart, durationMinutes)"
          @drag-activity-start="onActivityDragStart"
          @drag-activity-end="onActivityDragEnd"
          @open-catalog="$emit('openCatalog')"
        />
      </div>
    </div>
  </section>
</template>
