<script setup lang="ts">
import draggable from 'vuedraggable'
import {
  Dumbbell,
  GripVertical,
  Landmark,
  MapPin,
  Moon,
  Palette,
  RefreshCw,
  ShoppingBag,
  Star,
  Trees,
  Trash2,
  Utensils
} from 'lucide-vue-next'
import type { TripDay } from '~/types/trip'

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

const categoryName = (category?: string, subcategory?: string) => {
  const value = `${category || ''} ${subcategory || ''}`.toLowerCase()
  if (/night|club|bar|pub/.test(value)) return 'Nightlife'
  if (/food|restaurant|cafe|market|catering/.test(value)) return 'Food'
  if (/park|natur|garden|forest|beach/.test(value)) return 'Natur'
  if (/shop|commercial|mall/.test(value)) return 'Shopping'
  if (/sport|stadium|fitness/.test(value)) return 'Sport'
  if (/heritage|historic|monument|castle|geschichte/.test(value)) return 'Geschichte'
  return 'Kultur'
}

const categoryIcon = (category?: string, subcategory?: string) => {
  const icons = {
    Kultur: Palette,
    Geschichte: Landmark,
    Natur: Trees,
    Food: Utensils,
    Shopping: ShoppingBag,
    Nightlife: Moon,
    Sport: Dumbbell
  }
  return icons[categoryName(category, subcategory)]
}
</script>

<template>
  <div class="trip-day">
    <div class="trip-day-heading">
      <div class="day-number">{{ day.dayNumber }}</div>
      <div>
        <h3>{{ day.weekday || `Tag ${day.dayNumber}` }}</h3>
        <span class="muted">{{ day.travelDate ? formatDate(day.travelDate) : `${day.activities.length} Aktivitaeten` }}</span>
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
        <article class="schedule-item" :class="{ 'outside-window': !item.fitsAvailability }">
          <div class="schedule-time">
            <strong>{{ formatMinutes(item.scheduledStart) }}</strong>
            <span>{{ item.durationMinutes }} Min.</span>
          </div>
          <div class="schedule-marker" aria-hidden="true"><span /></div>
          <div class="schedule-content">
            <button class="drag-handle" type="button" title="Aktivitaet verschieben">
              <GripVertical :size="20" />
            </button>
            <div class="activity-icon" :title="categoryName(item.activity.category, item.activity.subcategory)">
              <component :is="categoryIcon(item.activity.category, item.activity.subcategory)" :size="22" :stroke-width="1.8" />
            </div>
            <div class="schedule-main">
              <span class="schedule-position">{{ categoryName(item.activity.category, item.activity.subcategory) }} &middot; Stopp {{ item.position }}</span>
              <h4>{{ item.activity.name }}</h4>
              <p v-if="item.activity.description" class="schedule-description">{{ item.activity.description }}</p>
              <div class="activity-facts">
                <span v-if="item.activity.rating != null"><Star :size="15" />{{ item.activity.rating.toFixed(1) }}</span>
                <span v-if="item.activity.address"><MapPin :size="15" />{{ item.activity.address }}</span>
              </div>
              <span v-if="!item.fitsAvailability" class="window-warning">Ausserhalb deines Zeitfensters</span>
            </div>
            <div class="schedule-actions">
              <button
                type="button"
                class="icon-button"
                title="Alternative Aktivitaet"
                :disabled="regeneratingActivityId === item.id"
                @click.stop="$emit('regenerateActivity', day.id, item.id)"
              ><RefreshCw :size="18" /></button>
              <button
                type="button"
                class="icon-button"
                title="Aktivitaet entfernen"
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
