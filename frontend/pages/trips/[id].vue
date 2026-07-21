<script setup lang="ts">
import { computed, onErrorCaptured, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  CalendarDays,
  Check,
  Martini,
  MapPin,
  MessageSquare,
  Palette,
  Plus,
  ShoppingBag,
  Trees,
  Utensils,
  X
} from 'lucide-vue-next'

const route = useRoute()
const workspace = useTripWorkspace()
const { request } = useApi()
const activeIndex = ref(0)
const catalogOpen = ref(false)
const dayViewError = ref('')
const dateDialogOpen = ref(false)
const rangeCalendarMonth = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1, 12))
const dateRangeStart = ref('')
const dateRangeEnd = ref('')
const dateRangePreviewEnd = ref('')
const dateRangeSelectedDate = ref('')
const dateRangeComplete = ref(false)
const dateRangeError = ref('')
const pendingDateRange = ref<{ startDate: string; endDate: string; planningDates: string[] } | null>(null)
const confirmRegenerationOpen = ref(false)
const interestMenuOpen = ref(false)
const feedbackInfoOpen = ref(false)
const feedbackMode = ref(false)
const feedbackTextOpen = ref(false)
const feedbackDirectMode = ref(false)
const feedbackText = ref('')
const feedbackSaving = ref(false)
const feedbackError = ref('')
const feedbackSuccess = ref(false)
const feedbackTarget = ref<{
  label: string
  selector: string
  screenshotDataUrl: string | null
  rect: { top: number; left: number; width: number; height: number }
} | null>(null)
const feedbackHoverRect = ref<{ top: number; left: number; width: number; height: number } | null>(null)

const interestCategories = [
  { key: 'SIGHTSEEING', label: 'Sehenswuerdigkeiten', icon: BookOpen },
  { key: 'CULTURE', label: 'Kultur & Museen', icon: Palette },
  { key: 'NATURE', label: 'Natur & Outdoor', icon: Trees },
  { key: 'FOOD', label: 'Essen & Cafes', icon: Utensils },
  { key: 'SHOPPING', label: 'Shopping & Maerkte', icon: ShoppingBag },
  { key: 'NIGHTLIFE', label: 'Nachtleben', icon: Martini }
]

const tripId = computed(() => Number(route.params.id))
const activeDay = computed(() => workspace.trip.value?.days?.[activeIndex.value] || null)
const dateSaving = computed(() => workspace.savingDates.value || workspace.fillingMissingPlan.value)
const flexibleDurationLabel = computed(() => {
  const days = workspace.trip.value?.daysCount || 0
  if (days === 3) return 'Ein Wochenende'
  if (days === 7) return 'Eine Woche'
  if (days === 14) return 'Zwei Wochen'
  return `${days} Tage`
})
const preferredMonthLabel = computed(() => {
  const value = workspace.trip.value?.preferredMonth
  if (!value) return ''
  const [year, month] = value.split('-').map(Number)
  return new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric' })
    .format(new Date(year, month - 1, 1, 12))
})
const flexibleTripLabel = computed(() =>
  [flexibleDurationLabel.value, preferredMonthLabel.value].filter(Boolean).join(' · ')
)

const tripDateLabel = computed(() => workspace.trip.value?.startDate
  ? `${formatDate(workspace.trip.value.startDate)} bis ${formatDate(workspace.trip.value.endDate)}`
  : flexibleTripLabel.value
)
const nextRangeCalendarMonth = computed(() => new Date(
  rangeCalendarMonth.value.getFullYear(),
  rangeCalendarMonth.value.getMonth() + 1,
  1,
  12
))
const rangeMonthLabel = computed(() => new Intl.DateTimeFormat('de-DE', {
  month: 'long',
  year: 'numeric'
}).format(rangeCalendarMonth.value))
const nextRangeMonthLabel = computed(() => new Intl.DateTimeFormat('de-DE', {
  month: 'long',
  year: 'numeric'
}).format(nextRangeCalendarMonth.value))
const selectedPlanningDates = computed(() => datesInRange(dateRangeStart.value, dateRangeEnd.value))
const selectedInterestKeys = computed(() => new Set(workspace.trip.value?.selectedInterests || []))
const selectedTripInterests = computed(() =>
  interestCategories.filter(interest => selectedInterestKeys.value.has(interest.key))
)
const availableTripInterests = computed(() =>
  interestCategories.filter(interest => !selectedInterestKeys.value.has(interest.key))
)
const feedbackTextboxStyle = computed(() => {
  if (!feedbackTarget.value || feedbackDirectMode.value || !import.meta.client) return {}
  return {
    top: `${Math.min(feedbackTarget.value.rect.top + feedbackTarget.value.rect.height + 10, window.innerHeight - 260)}px`,
    left: `${Math.min(feedbackTarget.value.rect.left, window.innerWidth - 360)}px`
  }
})

function datesInRange(from: string, until: string) {
  if (!from || !until) return []
  const start = from <= until ? from : until
  const end = from <= until ? until : from
  const dates: string[] = []
  const cursor = new Date(`${start}T12:00:00`)
  const last = new Date(`${end}T12:00:00`)
  while (cursor <= last && dates.length < 15) {
    dates.push(cursor.toISOString().slice(0, 10))
    cursor.setDate(cursor.getDate() + 1)
  }
  return dates
}

const openTripDateDialog = () => {
  if (!workspace.trip.value || dateSaving.value) return
  const trip = workspace.trip.value
  const source = trip.startDate || (trip.preferredMonth ? `${trip.preferredMonth}-01` : '')
  if (source) {
    const date = new Date(`${source}T12:00:00`)
    rangeCalendarMonth.value = new Date(date.getFullYear(), date.getMonth(), 1, 12)
  }
  dateRangeStart.value = trip.startDate || ''
  dateRangeEnd.value = trip.endDate || ''
  dateRangeSelectedDate.value = trip.startDate || ''
  dateRangePreviewEnd.value = ''
  dateRangeComplete.value = Boolean(trip.startDate && trip.endDate)
  dateRangeError.value = ''
  pendingDateRange.value = null
  confirmRegenerationOpen.value = false
  dateDialogOpen.value = true
}

const closeTripDateDialog = () => {
  if (dateSaving.value) return
  dateDialogOpen.value = false
  pendingDateRange.value = null
  confirmRegenerationOpen.value = false
}

const moveRangeMonth = (offset: number) => {
  rangeCalendarMonth.value = new Date(
    rangeCalendarMonth.value.getFullYear(),
    rangeCalendarMonth.value.getMonth() + offset,
    1,
    12
  )
}

const validRangeEnd = (date: string) => {
  if (!dateRangeStart.value) return false
  if (datesInRange(dateRangeStart.value, date).length > 14) {
    dateRangeError.value = 'Ein Reisezeitraum darf maximal 14 Tage umfassen.'
    return false
  }
  dateRangeError.value = ''
  return true
}

const previewRangeEnd = (date: string) => {
  if (dateRangeComplete.value || !validRangeEnd(date)) return
  dateRangePreviewEnd.value = date
}

const selectRangeDate = (date: string) => {
  dateRangeSelectedDate.value = date
  if (!dateRangeStart.value || dateRangeComplete.value) {
    dateRangeStart.value = date
    dateRangeEnd.value = ''
    dateRangePreviewEnd.value = ''
    dateRangeComplete.value = false
    dateRangeError.value = ''
    return
  }
  if (!validRangeEnd(date)) return
  dateRangeEnd.value = date
  dateRangePreviewEnd.value = ''
  dateRangeComplete.value = true
}

const clearDateRange = () => {
  dateRangeStart.value = ''
  dateRangeEnd.value = ''
  dateRangePreviewEnd.value = ''
  dateRangeSelectedDate.value = ''
  dateRangeComplete.value = false
  dateRangeError.value = ''
}

const requestDateSave = async () => {
  if (!workspace.trip.value || selectedPlanningDates.value.length === 0) {
    dateRangeError.value = 'Bitte waehle ein Start- und Enddatum aus.'
    return
  }
  const planningDates = selectedPlanningDates.value
  const payload = {
    startDate: planningDates[0],
    endDate: planningDates[planningDates.length - 1],
    planningDates
  }
  if (planningDates.length > workspace.trip.value.days.length) {
    pendingDateRange.value = payload
    confirmRegenerationOpen.value = true
    return
  }
  await savePendingDateRange(payload, false)
}

const savePendingDateRange = async (
  payload = pendingDateRange.value,
  fillMissing = true
) => {
  if (!payload) return
  confirmRegenerationOpen.value = false
  const updated = await workspace.updateTripDates(payload.startDate, payload.endDate, payload.planningDates)
  if (updated && fillMissing) {
    await workspace.fillMissingPlan()
  }
  if (!workspace.error.value) {
    dateDialogOpen.value = false
    pendingDateRange.value = null
    syncDayFromRoute()
  }
}

const setActiveIndex = async (index: number, replace = false) => {
  if (!workspace.trip.value || index < 0 || index >= workspace.trip.value.days.length) return
  activeIndex.value = index
  workspace.preloadUpcomingImages(index)
  const day = workspace.trip.value.days[index]?.dayNumber
  await navigateTo(
    { path: route.path, query: day ? { day } : undefined },
    { replace }
  )
}

const syncDayFromRoute = () => {
  if (!workspace.trip.value) return
  const requestedDay = Number(route.query.day)
  const index = workspace.trip.value.days.findIndex(day => day.dayNumber === requestedDay)
  activeIndex.value = index >= 0 ? index : 0
  dayViewError.value = ''
  workspace.preloadUpcomingImages(activeIndex.value)
}

const openCatalog = async () => {
  catalogOpen.value = true
  if (!workspace.catalog.value) {
    await workspace.loadCatalogAttractions()
  }
}

const addTripInterest = async (primaryInterest: string) => {
  await workspace.addTripInterest(primaryInterest)
  interestMenuOpen.value = false
}

const closeInterestMenu = () => {
  interestMenuOpen.value = false
}

const openFeedbackIntro = () => {
  feedbackInfoOpen.value = true
  feedbackError.value = ''
  feedbackSuccess.value = false
}

const startFeedbackHighlight = () => {
  feedbackInfoOpen.value = false
  feedbackMode.value = true
  feedbackDirectMode.value = false
  feedbackTextOpen.value = false
  feedbackTarget.value = null
  feedbackText.value = ''
}

const startDirectFeedback = () => {
  feedbackInfoOpen.value = false
  feedbackMode.value = false
  feedbackDirectMode.value = true
  feedbackTextOpen.value = true
  feedbackHoverRect.value = null
  feedbackTarget.value = null
  feedbackText.value = ''
}

const closeFeedbackMode = () => {
  feedbackInfoOpen.value = false
  feedbackMode.value = false
  feedbackTextOpen.value = false
  feedbackDirectMode.value = false
  feedbackHoverRect.value = null
  feedbackTarget.value = null
  feedbackText.value = ''
  feedbackError.value = ''
}

const closeFeedbackText = () => {
  feedbackTextOpen.value = false
  feedbackDirectMode.value = false
  feedbackTarget.value = null
  feedbackText.value = ''
}

const feedbackCandidateFromEvent = (event: MouseEvent) => {
  const target = event.target as HTMLElement | null
  if (!target || target.closest('[data-feedback-ui="true"]')) return null
  return target.closest<HTMLElement>(
    '.trip-interest-icon, .trip-interest-add-button, .trip-date-meta-button, .day-switcher button, .compact-activity, .activity-gallery, .activity-day-map, .orbit-heading-count, .catalog-open-button'
  )
}

const labelForFeedbackTarget = (element: HTMLElement) =>
  (element.getAttribute('aria-label')
    || element.getAttribute('title')
    || element.textContent
    || element.className
    || 'Markierter Bereich')
    .toString()
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 120)

const selectorForFeedbackTarget = (element: HTMLElement) => {
  if (element.id) return `#${CSS.escape(element.id)}`
  const classes = Array.from(element.classList).slice(0, 3).map(item => `.${CSS.escape(item)}`).join('')
  const tag = element.tagName.toLowerCase()
  return `${tag}${classes}`
}

const inlineStylesForClone = (source: Element, clone: Element) => {
  if (!(source instanceof HTMLElement) || !(clone instanceof HTMLElement)) return
  const computed = window.getComputedStyle(source)
  const properties = [
    'align-content', 'align-items', 'background', 'background-color', 'background-image',
    'background-position', 'background-repeat', 'background-size', 'border', 'border-color',
    'border-radius', 'border-style', 'border-width', 'box-shadow', 'box-sizing', 'color',
    'display', 'flex', 'flex-basis', 'flex-direction', 'flex-grow', 'flex-shrink', 'flex-wrap',
    'font', 'font-family', 'font-size', 'font-style', 'font-weight', 'gap', 'grid-template-columns',
    'grid-template-rows', 'height', 'justify-content', 'letter-spacing', 'line-height', 'margin',
    'max-height', 'max-width', 'min-height', 'min-width', 'object-fit', 'opacity', 'overflow',
    'padding', 'position', 'text-align', 'text-decoration', 'text-transform', 'transform',
    'white-space', 'width'
  ]
  clone.setAttribute('style', properties.map(property => `${property}:${computed.getPropertyValue(property)}`).join(';'))
  Array.from(source.children).forEach((child, index) => inlineStylesForClone(child, clone.children[index]))
}

const screenshotViewportWithHighlight = async (rect: { top: number; left: number; width: number; height: number }) => {
  const viewportWidth = Math.max(1, Math.ceil(window.innerWidth))
  const viewportHeight = Math.max(1, Math.ceil(window.innerHeight))
  const pageWidth = Math.max(viewportWidth, Math.ceil(document.documentElement.scrollWidth))
  const pageHeight = Math.max(viewportHeight, Math.ceil(document.documentElement.scrollHeight))
  const bodyClone = document.body.cloneNode(true) as HTMLElement

  bodyClone.querySelectorAll('[data-feedback-ui="true"], script').forEach(node => node.remove())
  inlineStylesForClone(document.body, bodyClone)
  bodyClone.style.position = 'absolute'
  bodyClone.style.left = `${-window.scrollX}px`
  bodyClone.style.top = `${-window.scrollY}px`
  bodyClone.style.width = `${pageWidth}px`
  bodyClone.style.minHeight = `${pageHeight}px`
  bodyClone.style.margin = '0'
  bodyClone.style.pointerEvents = 'none'

  const wrapper = document.createElement('div')
  wrapper.setAttribute('xmlns', 'http://www.w3.org/1999/xhtml')
  wrapper.style.position = 'relative'
  wrapper.style.overflow = 'hidden'
  wrapper.style.width = `${viewportWidth}px`
  wrapper.style.height = `${viewportHeight}px`
  wrapper.style.background = window.getComputedStyle(document.body).background || '#ffffff'
  wrapper.style.fontFamily = window.getComputedStyle(document.body).fontFamily
  wrapper.appendChild(bodyClone)

  const highlight = document.createElement('div')
  highlight.style.position = 'absolute'
  highlight.style.left = `${Math.max(0, Math.floor(rect.left))}px`
  highlight.style.top = `${Math.max(0, Math.floor(rect.top))}px`
  highlight.style.width = `${Math.max(1, Math.ceil(rect.width))}px`
  highlight.style.height = `${Math.max(1, Math.ceil(rect.height))}px`
  highlight.style.border = '4px solid #2f85cf'
  highlight.style.borderRadius = '12px'
  highlight.style.boxShadow = '0 0 0 9999px rgba(15, 23, 42, 0.24), 0 0 0 2px rgba(255, 255, 255, 0.9) inset'
  highlight.style.boxSizing = 'border-box'
  highlight.style.zIndex = '9999'
  highlight.style.pointerEvents = 'none'
  wrapper.appendChild(highlight)

  const marker = document.createElement('span')
  marker.textContent = 'Feedback'
  marker.style.position = 'absolute'
  marker.style.left = `${Math.max(8, Math.floor(rect.left))}px`
  marker.style.top = `${Math.max(8, Math.floor(rect.top) - 30)}px`
  marker.style.zIndex = '10000'
  marker.style.borderRadius = '999px'
  marker.style.padding = '5px 10px'
  marker.style.background = '#2f85cf'
  marker.style.color = '#ffffff'
  marker.style.font = '800 12px Inter, ui-sans-serif, system-ui, sans-serif'
  marker.style.boxShadow = '0 10px 24px rgba(10, 45, 87, 0.22)'
  wrapper.appendChild(marker)

  const html = new XMLSerializer().serializeToString(wrapper)
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="${viewportWidth}" height="${viewportHeight}">
      <foreignObject width="${viewportWidth}" height="${viewportHeight}">
        ${html}
      </foreignObject>
    </svg>`
  const image = new Image()
  image.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
  await new Promise((resolve, reject) => {
    image.onload = resolve
    image.onerror = reject
  })
  const canvas = document.createElement('canvas')
  canvas.width = viewportWidth
  canvas.height = viewportHeight
  canvas.getContext('2d')?.drawImage(image, 0, 0)
  return canvas.toDataURL('image/png')
}

const updateFeedbackHover = (event: MouseEvent) => {
  if (!feedbackMode.value || feedbackTextOpen.value) return
  const element = feedbackCandidateFromEvent(event)
  if (!element) {
    feedbackHoverRect.value = null
    return
  }
  const rect = element.getBoundingClientRect()
  feedbackHoverRect.value = { top: rect.top, left: rect.left, width: rect.width, height: rect.height }
}

const selectFeedbackTarget = async (event: MouseEvent) => {
  if (!feedbackMode.value || feedbackTextOpen.value) return
  const element = feedbackCandidateFromEvent(event)
  if (!element) return
  event.preventDefault()
  event.stopPropagation()
  const rect = element.getBoundingClientRect()
  const feedbackRect = { top: rect.top, left: rect.left, width: rect.width, height: rect.height }
  feedbackTarget.value = {
    label: labelForFeedbackTarget(element),
    selector: selectorForFeedbackTarget(element),
    screenshotDataUrl: await screenshotViewportWithHighlight(feedbackRect).catch(() => null),
    rect: feedbackRect
  }
  feedbackText.value = ''
  feedbackTextOpen.value = true
}

const submitFeedback = async () => {
  if (!feedbackText.value.trim() || feedbackSaving.value) return
  feedbackSaving.value = true
  feedbackError.value = ''
  feedbackSuccess.value = false
  try {
    await request('/feedback', {
      method: 'POST',
      body: {
        pageUrl: window.location.href,
        targetLabel: feedbackTarget.value?.label || (feedbackDirectMode.value ? 'Direktes Feedback' : ''),
        targetSelector: feedbackTarget.value?.selector || '',
        screenshotDataUrl: feedbackTarget.value?.screenshotDataUrl || '',
        description: feedbackText.value.trim()
      }
    })
    feedbackSuccess.value = true
    closeFeedbackText()
    feedbackMode.value = false
    setTimeout(() => { feedbackSuccess.value = false }, 2600)
  } catch (err: any) {
    feedbackError.value = err?.data?.message || 'Feedback konnte nicht gespeichert werden.'
  } finally {
    feedbackSaving.value = false
  }
}

const reorderDays = async (dayIds: number[]) => {
  const activeDayId = activeDay.value?.id
  await workspace.reorderDays(dayIds)
  if (!workspace.trip.value || !activeDayId) return
  const nextActiveIndex = workspace.trip.value.days.findIndex(day => day.id === activeDayId)
  if (nextActiveIndex >= 0) {
    await setActiveIndex(nextActiveIndex, true)
  }
}

watch(() => route.query.day, syncDayFromRoute)

onErrorCaptured((error) => {
  dayViewError.value = error instanceof Error ? error.message : 'Die Tagesansicht konnte nicht geladen werden.'
  return false
})

onMounted(async () => {
  window.addEventListener('click', closeInterestMenu)
  window.addEventListener('mousemove', updateFeedbackHover, true)
  window.addEventListener('click', selectFeedbackTarget, true)
  if (!Number.isFinite(tripId.value)) {
    await navigateTo('/calendar')
    return
  }
  await workspace.loadTrip(tripId.value)
  syncDayFromRoute()
  workspace.preloadUpcomingImages(activeIndex.value)
})

onUnmounted(() => {
  window.removeEventListener('click', closeInterestMenu)
  window.removeEventListener('mousemove', updateFeedbackHover, true)
  window.removeEventListener('click', selectFeedbackTarget, true)
})
</script>

<template>
  <div class="workspace-page trip-orbit-page">
    <AppNavigation
      :user="workspace.user.value"
      @logout="workspace.logout"
    />

    <main class="workspace-main orbit-page-main">
      <section v-if="workspace.loading.value" class="calendar-loading" aria-live="polite">
        <span />
        <p>Reise wird geladen...</p>
      </section>

      <template v-else-if="workspace.trip.value">
        <header class="orbit-page-heading">
          <div>
            <div class="trip-heading-title-row">
              <h1>{{ workspace.trip.value.city }}</h1>
              <div class="trip-interest-strip" aria-label="Ausgewaehlte Interessen">
                <span
                  v-for="interest in selectedTripInterests"
                  :key="interest.key"
                  class="trip-interest-icon"
                  :title="interest.label"
                  :aria-label="interest.label"
                >
                  <component :is="interest.icon" :size="17" :stroke-width="2" />
                </span>
                <div class="trip-interest-add">
                  <button
                    type="button"
                    class="trip-interest-add-button"
                    title="weiter Interesse hinzufuegen"
                    aria-label="weiter Interesse hinzufuegen"
                    :disabled="workspace.addingInterest.value || availableTripInterests.length === 0"
                    @click.stop="interestMenuOpen = !interestMenuOpen"
                  >
                    <Plus :size="16" :stroke-width="2.4" />
                  </button>
                  <div v-if="interestMenuOpen" class="trip-interest-menu" @click.stop>
                    <span>Interesse hinzufuegen</span>
                    <button
                      v-for="interest in availableTripInterests"
                      :key="interest.key"
                      type="button"
                      :disabled="workspace.addingInterest.value"
                      @click="addTripInterest(interest.key)"
                    >
                      <component :is="interest.icon" :size="16" :stroke-width="2" />
                      {{ interest.label }}
                    </button>
                    <p v-if="availableTripInterests.length === 0">Alle Interessen sind bereits ausgewaehlt.</p>
                  </div>
                </div>
              </div>
            </div>
            <p
              class="trip-date-meta-button"
              role="button"
              tabindex="0"
              @click="openTripDateDialog"
              @keydown.enter.prevent="openTripDateDialog"
              @keydown.space.prevent="openTripDateDialog"
            >
              <MapPin :size="16" />
              {{ workspace.trip.value.country || workspace.trip.value.state || 'Städtereise' }}
              <template v-if="workspace.trip.value.startDate">
                <span>&middot;</span><CalendarDays :size="16" />
                {{ formatDate(workspace.trip.value.startDate) }} bis {{ formatDate(workspace.trip.value.endDate) }}
              </template>
              <template v-else>
                <span>&middot;</span><CalendarDays :size="16" />
                {{ flexibleTripLabel }}
              </template>
            </p>
          </div>
          <div class="orbit-heading-actions">
            <button class="trip-feedback-button" type="button" @click="openFeedbackIntro">
              <MessageSquare :size="17" />
              Feedback
            </button>
            <div class="orbit-heading-count">
              <strong>{{ activeIndex + 1 }}</strong>
              <span>von {{ workspace.trip.value.days.length }} Tagen</span>
            </div>
          </div>
        </header>

        <p v-if="workspace.error.value" class="error workspace-error">{{ workspace.error.value }}</p>
        <p v-if="feedbackSuccess" class="feedback-success-toast" data-feedback-ui="true">
          Feedback gespeichert. Danke dir.
        </p>

        <section v-if="dateSaving" class="trip-date-loading" aria-live="polite">
          <span />
          <div>
            <strong>Reise wird aufgefuellt...</strong>
            <p>Die fehlenden Tage werden gerade passend zu deinen Interessen geplant.</p>
          </div>
        </section>

        <DayOrbitCarousel
          :trip="workspace.trip.value"
          :active-index="activeIndex"
          :deleting-activity-id="workspace.deletingActivityId.value"
          :regenerating-activity-id="workspace.regeneratingActivityId.value"
          @change="setActiveIndex"
          @update-availability="workspace.updateAvailability"
          @regenerate-activity="workspace.regenerateActivity"
          @remove-activity="workspace.removeActivity"
          @request-images="workspace.ensureActivityImages"
          @reorder-activities="workspace.reorderActivities"
          @reorder-days="reorderDays"
          @update-activity-timing="workspace.updateActivityTiming"
          @move-activity-to-day="workspace.moveActivityToDay"
          @open-catalog="openCatalog"
        />

        <section v-if="dayViewError && activeDay" class="trip-plan-fallback" aria-live="polite">
          <div>
            <span class="eyebrow">Tagesplan</span>
            <h2>Tag {{ activeDay.dayNumber }}</h2>
            <p>Die große Tagesansicht konnte gerade nicht geladen werden. Deine Stopps sind aber vorhanden.</p>
          </div>
          <ul>
            <li v-for="item in activeDay.activities" :key="item.id">
              <span>{{ formatMinutes(item.scheduledStart) }}</span>
              <strong>{{ item.activity.name }}</strong>
              <small>{{ item.activity.category || 'Aktivität' }}</small>
            </li>
          </ul>
        </section>

        <AttractionCatalogPanel
          :open="catalogOpen"
          :trip="workspace.trip.value"
          :catalog="workspace.catalog.value"
          :loading="workspace.catalogLoading.value"
          :error="workspace.catalogError.value"
          :adding-catalog-id="workspace.addingCatalogId.value"
          :default-day-id="workspace.trip.value.days[activeIndex]?.id"
          @close="catalogOpen = false"
          @refresh="workspace.loadCatalogAttractions"
          @add="workspace.addCatalogAttraction"
        />

        <Teleport to="body">
          <div
            v-if="feedbackHoverRect && feedbackMode && !feedbackTextOpen"
            class="feedback-highlight-box"
            data-feedback-ui="true"
            :style="{
              top: `${feedbackHoverRect.top}px`,
              left: `${feedbackHoverRect.left}px`,
              width: `${feedbackHoverRect.width}px`,
              height: `${feedbackHoverRect.height}px`
            }"
          />

          <div v-if="feedbackMode" class="feedback-mode-bar" data-feedback-ui="true">
            <strong>Feedback-Modus</strong>
            <span>Bereich anklicken, dann kurz beschreiben.</span>
            <button type="button" aria-label="Feedback-Modus verlassen" @click="closeFeedbackMode">
              <X :size="18" />
            </button>
          </div>

          <div v-if="feedbackInfoOpen" class="feedback-dialog-backdrop" data-feedback-ui="true" @click.self="feedbackInfoOpen = false">
            <section class="feedback-dialog" role="dialog" aria-modal="true" aria-label="Feedback geben">
              <button class="feedback-dialog-close" type="button" aria-label="Schliessen" @click="feedbackInfoOpen = false">
                <X :size="17" />
              </button>
              <span class="eyebrow">Feedback</span>
              <h2>Was moechtest du melden?</h2>
              <p>
                Du kannst entweder einen Bereich markieren und dazu eine kurze Beschreibung schreiben,
                oder dein Feedback direkt als Text absenden.
              </p>
              <div class="feedback-dialog-actions">
                <button type="button" @click="startDirectFeedback">Nur Text schreiben</button>
                <button type="button" class="primary" @click="startFeedbackHighlight">Bereich markieren</button>
              </div>
            </section>
          </div>

          <section
            v-if="feedbackTextOpen"
            class="feedback-textbox"
            data-feedback-ui="true"
            :class="{ direct: feedbackDirectMode }"
            :style="feedbackTextboxStyle"
          >
            <button class="feedback-dialog-close" type="button" aria-label="Feedback abbrechen" @click="closeFeedbackText">
              <X :size="16" />
            </button>
            <strong>{{ feedbackDirectMode ? 'Feedback schreiben' : feedbackTarget?.label }}</strong>
            <textarea v-model="feedbackText" rows="4" placeholder="Beschreibe kurz, was dir aufgefallen ist." />
            <p v-if="feedbackError" class="feedback-inline-error">{{ feedbackError }}</p>
            <button class="feedback-submit" type="button" :disabled="feedbackSaving || !feedbackText.trim()" @click="submitFeedback">
              <Check :size="16" />
              {{ feedbackSaving ? 'Wird gespeichert...' : 'Bestaetigen' }}
            </button>
          </section>

          <div v-if="dateDialogOpen" class="trip-date-dialog-backdrop" @click.self="closeTripDateDialog">
            <section
              class="range-picker-popover range-picker-popover--date trip-date-dialog"
              role="dialog"
              aria-modal="true"
              aria-label="Reisezeitraum festlegen"
              @click.stop
            >
              <div class="range-picker-popover-calendar">
                <button class="date-range-arrow" type="button" aria-label="Vorherige Monate" @click="moveRangeMonth(-1)">
                  <ArrowLeft :size="18" />
                </button>
                <section class="range-picker-month range-picker-month-left">
                  <h3>{{ rangeMonthLabel }}</h3>
                  <TravelCalendar
                    :month="rangeCalendarMonth"
                    :trips="[]"
                    :selected-date="dateRangeSelectedDate"
                    :range-start="dateRangeStart"
                    :range-end="dateRangeEnd"
                    :range-preview-end="dateRangePreviewEnd"
                    :range-complete="dateRangeComplete"
                    @select-date="selectRangeDate"
                    @range-hover="previewRangeEnd"
                  />
                </section>
                <section class="range-picker-month range-picker-month-right">
                  <h3>{{ nextRangeMonthLabel }}</h3>
                  <TravelCalendar
                    :month="nextRangeCalendarMonth"
                    :trips="[]"
                    :selected-date="dateRangeSelectedDate"
                    :range-start="dateRangeStart"
                    :range-end="dateRangeEnd"
                    :range-preview-end="dateRangePreviewEnd"
                    :range-complete="dateRangeComplete"
                    @select-date="selectRangeDate"
                    @range-hover="previewRangeEnd"
                  />
                </section>
                <button class="date-range-arrow" type="button" aria-label="Naechste Monate" @click="moveRangeMonth(1)">
                  <ArrowRight :size="18" />
                </button>
              </div>

              <p v-if="dateRangeError" class="calendar-range-error">{{ dateRangeError }}</p>
              <div class="range-picker-actions">
                <button class="range-picker-clear" type="button" :disabled="dateSaving" @click="clearDateRange">
                  Auswahl aufheben
                </button>
                <button
                  class="range-picker-confirm"
                  type="button"
                  :disabled="dateSaving || selectedPlanningDates.length === 0"
                  @click="requestDateSave"
                >
                  Bestaetigen
                </button>
              </div>

              <section
                v-if="confirmRegenerationOpen && pendingDateRange"
                class="trip-date-regeneration-dialog"
                role="alertdialog"
                aria-modal="true"
                aria-label="Fehlende Tage nachgenerieren"
              >
                <span class="eyebrow">Zeitraum erweitert</span>
                <h3>Fehlende Tage nachgenerieren?</h3>
                <p>
                  Du hast {{ pendingDateRange.planningDates.length }} Tage ausgewaehlt.
                  Aktuell hat die Reise {{ workspace.trip.value.days.length }} Tage.
                  Die zusaetzlichen Tage koennen jetzt automatisch geplant werden.
                </p>
                <div class="range-picker-actions">
                  <button class="range-picker-clear" type="button" :disabled="dateSaving" @click="confirmRegenerationOpen = false">
                    Abbrechen
                  </button>
                  <button class="range-picker-confirm" type="button" :disabled="dateSaving" @click="savePendingDateRange()">
                    Nachgenerieren
                  </button>
                </div>
              </section>
            </section>
          </div>
        </Teleport>
      </template>

      <section v-else class="empty-journeys">
        <div><strong>Reise nicht gefunden</strong><p>Vielleicht wurde sie bereits entfernt.</p></div>
        <NuxtLink class="button-link" to="/calendar">Zum Kalender</NuxtLink>
      </section>
    </main>

  </div>
</template>
