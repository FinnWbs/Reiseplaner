<script setup lang="ts">
import draggable from 'vuedraggable'
import {
  BookOpen,
  Dumbbell,
  GripVertical,
  MapPin,
  Martini,
  Palette,
  RefreshCw,
  ShoppingBag,
  Star,
  Trees,
  Trash2,
  Utensils
} from 'lucide-vue-next'
import type { TripDay } from '~/types/trip'
import { displayCategoryForActivity } from '~/utils/activityCategory'

defineProps<{
  day: TripDay
  deletingActivityId: number | null
  regeneratingActivityId: number | null
  formatMinutes: (minutes: number) => string
  formatDate: (date?: string) => string
}>()

defineEmits<{
  persistSchedule: []
  updateAvailability: [day: TripDay]
  regenerateActivity: [dayId: number, itemId: number]
  removeActivity: [dayId: number, itemId: number]
}>()

const categoryName = (activity: TripDay['activities'][number]['activity']) => displayCategoryForActivity(activity)

const categoryIcon = (activity: TripDay['activities'][number]['activity']) => {
  const icons = {
    Kultur: Palette,
    Geschichte: BookOpen,
    Natur: Trees,
    Food: Utensils,
    Shopping: ShoppingBag,
    Nightlife: Martini,
    Sport: Dumbbell
  }
  return icons[categoryName(activity)]
}
</script>

<template>
  <div class="trip-day">
    <div class="trip-day-heading">
      <div class="day-number">{{ day.dayNumber }}</div>
      <div>
        <h3>{{ day.weekday || `Tag ${day.dayNumber}` }}</h3>
        <span class="muted">{{ day.travelDate ? formatDate(day.travelDate) : `${day.activities.length} Aktivitäten` }}</span>
      </div>
    </div>

    <draggable
      v-model="day.activities"
      class="day-schedule"
      group="trip-activities"
      item-key="id"
      handle=".drag-handle"
      ghost-class="schedule-ghost"
      @end="$emit('persistSchedule')"
    >
      <template #item="{ element: item }">
        <article
          class="schedule-item"
          :class="[`category-${categoryName(item.activity).toLowerCase()}`, { 'outside-window': !item.fitsAvailability }]"
        >
          <div class="schedule-time">
            <strong>{{ formatMinutes(item.scheduledStart) }}</strong>
            <span>{{ item.durationMinutes }} Min.</span>
          </div>
          <div class="schedule-marker" aria-hidden="true"><span /></div>
          <div class="schedule-content">
            <button class="drag-handle" type="button" title="Aktivität verschieben">
              <GripVertical :size="20" />
            </button>
            <div class="activity-icon" :title="categoryName(item.activity)">
              <component :is="categoryIcon(item.activity)" :size="22" :stroke-width="1.8" />
            </div>
            <div class="schedule-main">
              <span class="schedule-position">{{ categoryName(item.activity) }} &middot; Stopp {{ item.position }}</span>
              <h4>{{ item.activity.name }}</h4>
              <p v-if="item.activity.description" class="schedule-description">{{ item.activity.description }}</p>
              <div class="activity-facts">
                <span v-if="item.activity.rating != null"><Star :size="15" />{{ item.activity.rating.toFixed(1) }}</span>
                <span v-if="item.activity.address"><MapPin :size="15" />{{ item.activity.address }}</span>
              </div>
              <span v-if="!item.fitsAvailability" class="window-warning">Außerhalb deines Zeitfensters</span>
            </div>
            <div class="schedule-actions">
              <button
                type="button"
                class="icon-button"
                title="Alternative Aktivität"
                :disabled="regeneratingActivityId === item.id"
                @click.stop="$emit('regenerateActivity', day.id, item.id)"
              ><RefreshCw :size="18" /></button>
              <button
                type="button"
                class="icon-button"
                title="Aktivität entfernen"
                :disabled="deletingActivityId === item.id"
                @click.stop="$emit('removeActivity', day.id, item.id)"
              ><Trash2 :size="18" /></button>
            </div>
          </div>
        </article>
      </template>
    </draggable>

    <AvailabilityRange
      :day="day"
      :format-minutes="formatMinutes"
      @update="$emit('updateAvailability', $event)"
    />
  </div>
</template>
