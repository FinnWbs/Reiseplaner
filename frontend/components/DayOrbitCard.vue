<script setup lang="ts">
import { CalendarDays, Sparkles } from 'lucide-vue-next'
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
    :aria-label="`${day.weekday || `Tag ${day.dayNumber}`}, ${day.activities.length} Aktivitäten`"
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
      <DayAvailabilityMenu :day="day" @update="$emit('updateAvailability', $event)" />
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
              <p>Hier ist noch Platz für spontane Entdeckungen.</p>
            </div>
          </div>

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
      <p>{{ day.activities.length === 1 ? 'Aktivität' : 'Aktivitäten' }}</p>
      <small v-if="day.activities[0]">Start um {{ formatMinutes(day.activities[0].scheduledStart) }}</small>
    </div>
  </article>
</template>
