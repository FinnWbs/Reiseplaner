<script setup lang="ts">
import {
  BookOpen,
  Check,
  ChevronDown,
  Clock3,
  Dumbbell,
  ExternalLink,
  GripVertical,
  MapPin,
  Martini,
  Palette,
  RefreshCw,
  ShoppingBag,
  Star,
  Trees,
  Utensils
} from 'lucide-vue-next'
import type { TripActivity } from '~/types/trip'
import { displayCategoryForActivity } from '~/utils/activityCategory'
import { googleMapsUrl } from '~/utils/maps'

const props = defineProps<{
  item: TripActivity
  city: string
  selected?: boolean
  timingOpen?: boolean
  regenerating?: boolean
}>()

const emit = defineEmits<{
  select: [itemId: number]
  requestTimingOpen: [itemId: number]
  closeTiming: []
  updateTiming: [payload: { itemId: number; scheduledStart: number; durationMinutes: number }]
  actionMenu: [itemId: number, x: number, y: number]
}>()

const expanded = ref(false)
const draftStart = ref(props.item.scheduledStart)
const draftDuration = ref(props.item.durationMinutes)
const manualTime = ref(formatTimeValue(props.item.scheduledStart))
const timeButtonRef = ref<HTMLElement | null>(null)
const timeOptionsRef = ref<HTMLElement | null>(null)
const editorStyle = ref<Record<string, string>>({})

const timeOptions = Array.from({ length: 48 }, (_, index) => index * 30)
const durationOptions = Array.from({ length: 16 }, (_, index) => (index + 1) * 30)

const categoryName = computed(() => displayCategoryForActivity(props.item.activity))

const icon = computed(() => ({
  Kultur: Palette,
  Geschichte: BookOpen,
  Natur: Trees,
  Food: Utensils,
  Shopping: ShoppingBag,
  Nightlife: Martini,
  Sport: Dumbbell
})[categoryName.value])

const isTimingOpen = computed(() => Boolean(props.timingOpen))
const parsedManualTime = computed(() => parseTimeInput(manualTime.value))
const hasValidManualTime = computed(() => parsedManualTime.value != null)
const hasTimingChanges = computed(() =>
  (parsedManualTime.value ?? draftStart.value) !== props.item.scheduledStart
  || draftDuration.value !== props.item.durationMinutes
)

watch(() => [props.item.scheduledStart, props.item.durationMinutes], () => {
  if (isTimingOpen.value) return
  draftStart.value = props.item.scheduledStart
  draftDuration.value = props.item.durationMinutes
  manualTime.value = formatTimeValue(props.item.scheduledStart)
})

watch(isTimingOpen, (open) => {
  if (!open) return
  draftStart.value = props.item.scheduledStart
  draftDuration.value = props.item.durationMinutes
  manualTime.value = formatTimeValue(props.item.scheduledStart)
  updateEditorPosition()
  scrollSelectedTimeIntoView()
})

function formatTimeValue(minutes: number) {
  const normalized = Math.max(0, Math.min(1439, Number.isFinite(minutes) ? minutes : 0))
  const hours = Math.floor(normalized / 60).toString().padStart(2, '0')
  const mins = (normalized % 60).toString().padStart(2, '0')
  return `${hours}:${mins}`
}

function parseTimeInput(value: string) {
  const trimmed = value.trim()
  const match = /^([01]?\d|2[0-3])(?::?([0-5]\d))?$/.exec(trimmed)
  if (!match) return null
  return Number(match[1]) * 60 + Number(match[2] || '00')
}

const scrollSelectedTimeIntoView = async () => {
  await nextTick()
  if (!timeOptionsRef.value) return
  const optionHeight = 34
  const selectedIndex = Math.max(0, Math.floor(draftStart.value / 30))
  timeOptionsRef.value.scrollTop = selectedIndex * optionHeight
}

const updateEditorPosition = async () => {
  await nextTick()
  if (!import.meta.client || !timeButtonRef.value) return
  const rect = timeButtonRef.value.getBoundingClientRect()
  const width = 260
  const margin = 12
  const left = Math.max(margin, Math.min(rect.left - 4, window.innerWidth - width - margin))
  const top = Math.max(margin, rect.bottom + 8)
  editorStyle.value = {
    left: `${left}px`,
    top: `${top}px`,
    width: `${width}px`
  }
}

const openTimingMenu = (event: MouseEvent) => {
  event.preventDefault()
  event.stopPropagation()
  emit('select', props.item.id)
  emit('requestTimingOpen', props.item.id)
}

const selectDraftStart = (minutes: number) => {
  draftStart.value = minutes
  manualTime.value = formatTimeValue(minutes)
}

const closeTimingMenu = () => {
  emit('closeTiming')
  draftStart.value = props.item.scheduledStart
  draftDuration.value = props.item.durationMinutes
  manualTime.value = formatTimeValue(props.item.scheduledStart)
}

const applyManualTime = () => {
  if (parsedManualTime.value == null) return
  draftStart.value = parsedManualTime.value
  manualTime.value = formatTimeValue(parsedManualTime.value)
}

const saveTiming = () => {
  applyManualTime()
  if (!hasValidManualTime.value) return
  emit('updateTiming', {
    itemId: props.item.id,
    scheduledStart: draftStart.value,
    durationMinutes: draftDuration.value
  })
  emit('closeTiming')
}

const closeTimingOnOutsideClick = () => {
  if (isTimingOpen.value) closeTimingMenu()
}

const closeTimingOnEscape = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && isTimingOpen.value) {
    event.stopPropagation()
    closeTimingMenu()
  }
}

onMounted(() => {
  window.addEventListener('click', closeTimingOnOutsideClick)
  window.addEventListener('keydown', closeTimingOnEscape)
  window.addEventListener('resize', updateEditorPosition)
  window.addEventListener('scroll', updateEditorPosition, true)
})

onUnmounted(() => {
  window.removeEventListener('click', closeTimingOnOutsideClick)
  window.removeEventListener('keydown', closeTimingOnEscape)
  window.removeEventListener('resize', updateEditorPosition)
  window.removeEventListener('scroll', updateEditorPosition, true)
})

const getMenuAnchor = (element: HTMLElement) => {
  const rect = element.getBoundingClientRect()
  return {
    x: rect.right - 232,
    y: rect.top + 10
  }
}

const openActionMenu = (event: MouseEvent) => {
  event.preventDefault()
  event.stopPropagation()
  emit('select', props.item.id)
  emit('actionMenu', props.item.id, event.clientX, event.clientY)
}

const openKeyboardActionMenu = (event: KeyboardEvent) => {
  if (event.key !== 'ContextMenu' && !(event.shiftKey && event.key === 'F10')) return
  const element = event.currentTarget as HTMLElement
  const anchor = getMenuAnchor(element)
  event.preventDefault()
  event.stopPropagation()
  emit('select', props.item.id)
  emit('actionMenu', props.item.id, anchor.x, anchor.y)
}
</script>

<template>
  <article
    class="compact-activity"
    :class="[`category-${categoryName.toLowerCase()}`, { expanded, selected, regenerating, 'outside-window': !item.fitsAvailability }]"
    tabindex="0"
    @click="emit('select', item.id)"
    @contextmenu="openActionMenu"
    @keydown.enter="emit('select', item.id)"
    @keydown="openKeyboardActionMenu"
  >
    <button
      class="compact-activity-drag-handle"
      type="button"
      title="Aktivität verschieben"
      aria-label="Aktivität per Drag and Drop verschieben"
      @click.stop.prevent
    >
      <GripVertical :size="18" aria-hidden="true" />
    </button>
    <div class="compact-activity-time" @click.stop>
      <button
        ref="timeButtonRef"
        class="compact-activity-time-button"
        type="button"
        :aria-expanded="isTimingOpen"
        aria-haspopup="dialog"
        title="Zeit und Dauer bearbeiten"
        @click="openTimingMenu"
      >
        <strong>{{ formatMinutes(item.scheduledStart) }}</strong>
        <span>{{ item.durationMinutes }} Min.</span>
      </button>
      <Teleport to="body">
        <div
          v-if="isTimingOpen"
          class="activity-time-editor"
          :style="editorStyle"
          role="dialog"
          aria-modal="false"
          aria-label="Zeit und Dauer bearbeiten"
          @click.stop
        >
          <label class="activity-time-editor-field">
            <span>Startzeit</span>
            <input
              v-model="manualTime"
              type="text"
              inputmode="numeric"
              placeholder="12:00"
              :aria-invalid="!hasValidManualTime"
              @keydown.enter.prevent="saveTiming"
              @blur="applyManualTime"
            >
          </label>
          <div ref="timeOptionsRef" class="activity-time-options" aria-label="Startzeit auswahlen">
            <button
              v-for="value in timeOptions"
              :key="value"
              type="button"
              :class="{ active: value === draftStart }"
              @click="selectDraftStart(value)"
            >
              <Clock3 :size="14" aria-hidden="true" />
              {{ formatMinutes(value) }}
            </button>
          </div>
          <label class="activity-time-editor-field">
            <span>Dauer</span>
            <select v-model.number="draftDuration">
              <option v-for="value in durationOptions" :key="value" :value="value">
                {{ value }} Min.
              </option>
            </select>
          </label>
          <small v-if="!hasValidManualTime" class="activity-time-editor-error">Bitte HH:MM eingeben.</small>
          <div class="activity-time-editor-actions">
            <button type="button" class="ghost" @click="closeTimingMenu">Abbrechen</button>
            <button type="button" :disabled="!hasValidManualTime || !hasTimingChanges" @click="saveTiming">
              <Check :size="15" aria-hidden="true" />
              Speichern
            </button>
          </div>
        </div>
      </Teleport>
    </div>
    <span class="compact-activity-line" aria-hidden="true"><i /></span>
    <div class="compact-activity-icon">
      <component :is="icon" :size="20" :stroke-width="1.8" />
      <RefreshCw
        v-if="regenerating"
        class="compact-activity-regenerating-icon spinning"
        :size="18"
        :stroke-width="2.1"
        aria-hidden="true"
      />
    </div>
    <div class="compact-activity-copy">
      <span class="compact-category">{{ categoryName }} &middot; Stopp {{ item.position }}</span>
      <h3>{{ item.activity.name }}</h3>
      <p v-if="item.activity.description">{{ item.activity.description }}</p>
      <div v-if="expanded" class="compact-activity-details">
        <span v-if="item.activity.rating != null"><Star :size="14" />{{ item.activity.rating.toFixed(1) }}</span>
        <span v-if="item.activity.address"><MapPin :size="14" />{{ item.activity.address }}</span>
        <span v-if="!item.fitsAvailability" class="window-warning">Außerhalb des Zeitfensters</span>
      </div>
    </div>
    <div class="compact-activity-actions">
      <a
        class="icon-button"
        :href="googleMapsUrl(item, city)"
        target="_blank"
        rel="noopener noreferrer"
        title="In Google Maps öffnen"
        aria-label="In Google Maps öffnen"
        @click.stop
      ><ExternalLink :size="17" /></a>
      <button
        class="icon-button"
        type="button"
        :title="expanded ? 'Details schließen' : 'Details anzeigen'"
        :aria-label="expanded ? 'Details schließen' : 'Details anzeigen'"
        @click.stop="expanded = !expanded"
      ><ChevronDown :size="17" /></button>
    </div>
  </article>
</template>
