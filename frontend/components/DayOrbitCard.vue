<script setup lang="ts">
import { CalendarDays, RefreshCw, Sparkles, Trash2 } from 'lucide-vue-next'
import draggable from 'vuedraggable'
import type { TripActivity, TripDay } from '~/types/trip'
import { fallbackImageCategoryForActivity } from '~/utils/activityCategory'

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
const openTimingItemId = ref<number | null>(null)
const galleryError = ref(false)
const contentRef = ref<HTMLElement | null>(null)
const localActivities = ref<TripActivity[]>([])
let activityLongPressTimer: ReturnType<typeof setTimeout> | null = null
let activityLongPressOrigin: { x: number; y: number } | null = null
const selectedActivity = computed(() =>
  props.day.activities.find(item => item.id === selectedActivityId.value) || props.day.activities[0] || null
)

const fallbackCategory = computed(() => fallbackImageCategoryForActivity(selectedActivity.value?.activity))

const fallbackImageUrl = computed(() => `/images/activity-fallbacks/${fallbackCategory.value}-01.png`)

watch(() => props.day.id, () => {
  localActivities.value = [...props.day.activities]
  selectedActivityId.value = props.day.activities[0]?.id || null
  activityContextMenu.value = null
  openTimingItemId.value = null
  galleryError.value = false
  nextTick(() => {
    if (contentRef.value) contentRef.value.scrollTop = 0
  })
})

watch(() => props.day.activities, (activities) => {
  localActivities.value = [...activities]
  if (!activities.length) {
    selectedActivityId.value = null
    return
  }
  if (!activities.some(item => item.id === selectedActivityId.value)) {
    selectedActivityId.value = activities[0].id
  }
}, { immediate: true, deep: true })

watch(selectedActivityId, () => {
  galleryError.value = false
})

const componentName = (instance: any) =>
  instance?.$options?.name || instance?.$options?.__name || instance?.$?.type?.name || instance?.$?.type?.__name || null

const isActivityGalleryError = (instance: any) => {
  let current = instance
  while (current) {
    if (componentName(current) === 'ActivityGallery') return true
    current = current.$parent || current.parent || null
  }
  return false
}

onErrorCaptured((error, instance, info) => {
  const galleryRelated = isActivityGalleryError(instance)
  if (galleryRelated) {
    if (import.meta.dev) console.error('[DayOrbitCard] Activity gallery failed', error, info)
    galleryError.value = true
  } else if (import.meta.dev) {
    console.warn('[DayOrbitCard] Child component error ignored', error, info)
  }
  return false
})

const emit = defineEmits<{
  select: []
  updateAvailability: [day: TripDay]
  regenerateActivity: [dayId: number, itemId: number]
  removeActivity: [dayId: number, itemId: number]
  requestImages: [activityId: number]
  reorderActivities: [dayId: number, activityItemIds: number[]]
  updateActivityTiming: [dayId: number, itemId: number, scheduledStart: number, durationMinutes: number]
  dragActivityStart: [dayId: number, itemId: number]
  dragActivityEnd: []
  openCatalog: []
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
  openTimingItemId.value = null
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

const openTimingMenu = (itemId: number) => {
  closeActivityContextMenu()
  selectedActivityId.value = itemId
  openTimingItemId.value = itemId
}

const closeTimingMenu = () => {
  openTimingItemId.value = null
}

const updateActivityTiming = (payload: { itemId: number; scheduledStart: number; durationMinutes: number }) => {
  closeTimingMenu()
  emit('updateActivityTiming', props.day.id, payload.itemId, payload.scheduledStart, payload.durationMinutes)
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

const handleActivityDragStart = (event: { oldIndex?: number }) => {
  clearActivityLongPress()
  closeActivityContextMenu()
  closeTimingMenu()
  const item = localActivities.value[event.oldIndex ?? -1]
  if (!item) return
  selectedActivityId.value = item.id
  emit('dragActivityStart', props.day.id, item.id)
}

const handleActivityDragEnd = () => {
  const orderedIds = localActivities.value.map(item => item.id)
  const currentIds = props.day.activities.map(item => item.id)
  if (orderedIds.length === currentIds.length && orderedIds.some((id, index) => id !== currentIds[index])) {
    emit('reorderActivities', props.day.id, orderedIds)
  }
  emit('dragActivityEnd')
}

const handleEscape = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    closeActivityContextMenu()
    closeTimingMenu()
  }
}

onMounted(() => {
  window.addEventListener('click', closeActivityContextMenu)
  window.addEventListener('keydown', handleEscape)
})

onUnmounted(() => {
  clearActivityLongPress()
  closeTimingMenu()
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
      <button class="catalog-open-button" type="button" @click.stop="$emit('openCatalog')">
        <Sparkles :size="17" />
        Highlights entdecken
      </button>
    </header>

    <template v-if="active">
      <div class="orbit-active-layout">
        <div ref="contentRef" class="orbit-day-content">
          <div v-if="day.activities.length > 0" class="day-activity-experience">
            <draggable
              v-model="localActivities"
              class="compact-timeline compact-timeline-draggable"
              item-key="id"
              handle=".compact-activity-drag-handle"
              ghost-class="compact-activity-ghost"
              chosen-class="compact-activity-chosen"
              drag-class="compact-activity-dragging"
              :animation="150"
              @start="handleActivityDragStart"
              @end="handleActivityDragEnd"
            >
              <template #item="{ element: item }">
              <CompactActivityRow
                :key="item.id"
                :item="item"
                :city="city"
                :selected="selectedActivityId === item.id"
                :timing-open="openTimingItemId === item.id"
                @select="selectActivity"
                @request-timing-open="openTimingMenu"
                @close-timing="closeTimingMenu"
                @update-timing="updateActivityTiming"
                @action-menu="openActivityContextMenu"
                @pointerdown="startActivityLongPress($event, item)"
                @pointermove="moveActivityLongPress"
                @pointerup="clearActivityLongPress"
                @pointercancel="clearActivityLongPress"
                @pointerleave="clearActivityLongPress"
              />
              </template>
            </draggable>
            <ActivityGallery
              v-if="day.activities.length && !galleryError"
              :key="(selectedActivity || day.activities[0])?.id"
              :activity="selectedActivity || day.activities[0]"
              :city="city"
              @request-images="$emit('requestImages', $event)"
            />
            <section
              v-else
              class="activity-gallery gallery-fallback-only"
              aria-label="Bildplatzhalter"
            >
              <div class="activity-gallery-stage">
                <figure class="activity-gallery-slide">
                  <img
                    class="gallery-fallback-image"
                    :src="fallbackImageUrl"
                    :alt="`Bildplatzhalter für ${selectedActivity?.activity.name || 'Aktivität'}`"
                    loading="eager"
                  >
                  <figcaption>
                    <div>
                      <span class="gallery-kicker">Galerie</span>
                      <strong>{{ selectedActivity?.activity.name || 'Aktivität' }}</strong>
                      <small>TravelMate Standardbild</small>
                    </div>
                    <span class="gallery-counter">1 / 1</span>
                  </figcaption>
                </figure>
                <div class="gallery-badge">
                  Galerie
                </div>
              </div>
            </section>
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
