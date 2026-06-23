<script setup lang="ts">
import { CalendarDays, Clock3, Sparkles } from 'lucide-vue-next'
import type { TripDay } from '~/types/trip'

const props = defineProps<{
  day: TripDay
  city: string
  active: boolean
  deletingActivityId: number | null
  regeneratingActivityId: number | null
}>()

const selectedActivityId = ref<number | null>(null)

watch(() => props.day.id, () => {
  selectedActivityId.value = null
})

defineEmits<{
  select: []
  updateAvailability: [day: TripDay]
  regenerateActivity: [dayId: number, itemId: number]
  removeActivity: [dayId: number, itemId: number]
}>()
</script>

<template>
  <article
    class="orbit-day-card"
    :class="{ active }"
    :tabindex="active ? 0 : -1"
    :aria-label="`${day.weekday || `Tag ${day.dayNumber}`}, ${day.activities.length} Aktivitaeten`"
    @click="!active && $emit('select')"
    @keydown.enter="!active && $emit('select')"
  >
    <header class="orbit-card-header">
      <div class="orbit-day-number">{{ day.dayNumber }}</div>
      <div>
        <span class="orbit-kicker">{{ active ? 'Dein Reisetag' : `Tag ${day.dayNumber}` }}</span>
        <h2>{{ day.weekday || `Tag ${day.dayNumber}` }}</h2>
        <p v-if="day.travelDate"><CalendarDays :size="15" />{{ formatDate(day.travelDate) }}</p>
      </div>
      <span class="orbit-time-window"><Clock3 :size="15" />{{ formatMinutes(day.availableFrom) }}–{{ formatMinutes(day.availableUntil) }}</span>
    </header>

    <template v-if="active">
      <div class="orbit-active-layout">
        <div class="orbit-day-content">
          <div v-if="day.activities.length > 0" class="compact-timeline">
            <CompactActivityRow
              v-for="item in day.activities"
              :key="item.id"
              :item="item"
              :city="city"
              :selected="selectedActivityId === item.id"
              :deleting="deletingActivityId === item.id"
              :regenerating="regeneratingActivityId === item.id"
              @select="selectedActivityId = $event"
              @regenerate="$emit('regenerateActivity', day.id, $event)"
              @remove="$emit('removeActivity', day.id, $event)"
            />
          </div>
          <div v-else class="empty-day">
            <Sparkles :size="27" />
            <div>
              <strong>Dieser Tag ist noch offen</strong>
              <p>Hier ist noch Platz fuer spontane Entdeckungen.</p>
            </div>
          </div>

          <AvailabilityRange
            :day="day"
            :format-minutes="formatMinutes"
            @update="$emit('updateAvailability', $event)"
          />
        </div>

        <ActivityDayMap
          :activities="day.activities"
          :city="city"
          :selected-activity-id="selectedActivityId"
          @select="selectedActivityId = $event"
        />
      </div>
    </template>

    <div v-else class="orbit-card-preview">
      <span>{{ day.activities.length }}</span>
      <p>{{ day.activities.length === 1 ? 'Aktivitaet' : 'Aktivitaeten' }}</p>
      <small v-if="day.activities[0]">Start um {{ formatMinutes(day.activities[0].scheduledStart) }}</small>
    </div>
  </article>
</template>
