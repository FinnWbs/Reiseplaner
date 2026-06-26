<script setup lang="ts">
import { CalendarDays, RefreshCw, Sparkles, Trash2 } from 'lucide-vue-next'
import type { TripActivity, TripDay } from '~/types/trip'

const props = defineProps<{
  day: TripDay
  city: string
  active: boolean
  deletingActivityId: number | null
  regeneratingActivityId: number | null
}>()

const selectedActivityId = ref<number | null>(null)
const activityContextMenu = ref<{ x: number; y: number; item: TripActivity } | null>(null)
const activityLongPressId = ref<number | null>(null)
let activityLongPressTimer: ReturnType<typeof setTimeout> | null = null
let activityLongPressOrigin: { x: number; y: number } | null = null
const selectedActivity = computed(() =>
  props.day.activities.find(item => item.id === selectedActivityId.value) || props.day.activities[0] || null
)

watch(() => props.day.id, () => {
  selectedActivityId.value = props.day.activities[0]?.id || null
  activityContextMenu.value = null
})

watch(() => props.day.activities, (activities) => {
  if (!activities.length) {
    selectedActivityId.value = null
    return
  }
  if (!activities.some(item => item.id === selectedActivityId.value)) {
    selectedActivityId.value = activities[0].id
  }
}, { immediate: true, deep: true })

const emit = defineEmits<{
  select: []
  updateAvailability: [day: TripDay]
  regenerateActivity: [dayId: number, itemId: number]
  removeActivity: [dayId: number, itemId: number]
}>()

const getActivityMenuPosition = (x: number, y: number) => {
  if (!import.meta.client) return { x, y }
  const container = document.querySelector('.orbit-day-card.active')?.getBoundingClientRect()
  if (container) {
    const localX = x - container.left
    const localY = y - container.top
    return {
      x: Math.max(12, Math.min(localX, container.width - 232)),
      y: Math.max(12, Math.min(localY, container.height - 112))
    }
  }

  return {
    x: Math.max(12, Math.min(x, window.innerWidth - 232)),
    y: Math.max(12, Math.min(y, window.innerHeight - 112))
  }
}

const getActivityMenuAnchor = (event: PointerEvent) => {
  const element = event.currentTarget as HTMLElement | null
  if (!element) return { x: event.clientX, y: event.clientY }
  const rect = element.getBoundingClientRect()
  return {
    x: rect.right - 232,
    y: rect.top + 10
  }
}

const openActivityContextMenu = (itemId: number, x: number, y: number) => {
  const item = props.day.activities.find(activity => activity.id === itemId)
  if (!item) return
  const position = getActivityMenuPosition(x, y)
  selectedActivityId.value = item.id
  activityContextMenu.value = { ...position, item }
}

const closeActivityContextMenu = () => {
  activityContextMenu.value = null
}

const clearActivityLongPress = () => {
  if (activityLongPressTimer) clearTimeout(activityLongPressTimer)
  activityLongPressTimer = null
  activityLongPressOrigin = null
}

const startActivityLongPress = (event: PointerEvent, item: TripActivity) => {
  if (event.pointerType === 'mouse') return
  clearActivityLongPress()
  const anchor = getActivityMenuAnchor(event)
  activityLongPressOrigin = { x: event.clientX, y: event.clientY }
  activityLongPressTimer = setTimeout(() => {
    activityLongPressId.value = item.id
    openActivityContextMenu(item.id, anchor.x, anchor.y)
    activityLongPressTimer = null
  }, 550)
}

const moveActivityLongPress = (event: PointerEvent) => {
  if (!activityLongPressOrigin) return
  if (Math.hypot(event.clientX - activityLongPressOrigin.x, event.clientY - activityLongPressOrigin.y) > 10) {
    clearActivityLongPress()
  }
}

const selectActivity = (itemId: number) => {
  clearActivityLongPress()
  if (activityLongPressId.value === itemId) {
    activityLongPressId.value = null
    return
  }
  selectedActivityId.value = itemId
}

const regenerateFromMenu = () => {
  if (!activityContextMenu.value) return
  const itemId = activityContextMenu.value.item.id
  closeActivityContextMenu()
  emit('regenerateActivity', props.day.id, itemId)
}

const removeFromMenu = () => {
  if (!activityContextMenu.value) return
  const itemId = activityContextMenu.value.item.id
  closeActivityContextMenu()
  emit('removeActivity', props.day.id, itemId)
}

const handleEscape = (event: KeyboardEvent) => {
  if (event.key === 'Escape') closeActivityContextMenu()
}

onMounted(() => {
  window.addEventListener('click', closeActivityContextMenu)
  window.addEventListener('keydown', handleEscape)
})

onUnmounted(() => {
  clearActivityLongPress()
  window.removeEventListener('click', closeActivityContextMenu)
  window.removeEventListener('keydown', handleEscape)
})
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
          <div v-if="day.activities.length > 0" class="day-activity-experience">
            <div class="compact-timeline">
              <CompactActivityRow
                v-for="item in day.activities"
                :key="item.id"
                :item="item"
                :city="city"
                :selected="selectedActivityId === item.id"
                @select="selectActivity"
                @action-menu="openActivityContextMenu"
                @pointerdown="startActivityLongPress($event, item)"
                @pointermove="moveActivityLongPress"
                @pointerup="clearActivityLongPress"
                @pointercancel="clearActivityLongPress"
                @pointerleave="clearActivityLongPress"
              />
            </div>
            <ActivityGallery
              v-if="selectedActivity"
              :activity="selectedActivity"
              :city="city"
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

      <div
        v-if="activityContextMenu"
        class="calendar-context-menu activity-context-menu"
        :style="{ left: `${activityContextMenu.x}px`, top: `${activityContextMenu.y}px` }"
        @click.stop
      >
        <button
          type="button"
          :disabled="regeneratingActivityId === activityContextMenu.item.id"
          @click="regenerateFromMenu"
        >
          <RefreshCw
            :class="{ spinning: regeneratingActivityId === activityContextMenu.item.id }"
            :size="16"
          />{{ regeneratingActivityId === activityContextMenu.item.id ? 'Alternative wird gesucht' : 'Neue Aktivität generieren' }}
        </button>
        <button
          class="context-menu-danger"
          type="button"
          :disabled="deletingActivityId === activityContextMenu.item.id"
          @click="removeFromMenu"
        >
          <Trash2 :size="16" />{{ deletingActivityId === activityContextMenu.item.id ? 'Wird entfernt' : 'Aktivität entfernen' }}
        </button>
      </div>
    </template>

    <div v-else class="orbit-card-preview">
      <span>{{ day.activities.length }}</span>
      <p>{{ day.activities.length === 1 ? 'Aktivität' : 'Aktivitäten' }}</p>
      <small v-if="day.activities[0]">Start um {{ formatMinutes(day.activities[0].scheduledStart) }}</small>
    </div>
  </article>
</template>
