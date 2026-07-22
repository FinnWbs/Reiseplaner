<script setup lang="ts">
import type { Trip } from '~/types/trip'

type CalendarCell = {
  key: string
  date: Date
  iso: string
  dayNumber: number
  inMonth: boolean
  isToday: boolean
}

const props = defineProps<{
  month: Date
  trips: Trip[]
  selectedDate: string
  rangeStart: string
  rangeEnd: string
  rangePreviewEnd?: string
  rangeComplete?: boolean
}>()

const emit = defineEmits<{
  selectDate: [date: string]
  openTrip: [trip: Trip, date: string]
  rangeHover: [date: string]
  rangeContext: [date: string, x: number, y: number]
}>()

const weekdays = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']
const isoDate = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const cells = computed<CalendarCell[]>(() => {
  const first = new Date(props.month.getFullYear(), props.month.getMonth(), 1, 12)
  const mondayOffset = (first.getDay() + 6) % 7
  const start = new Date(first)
  start.setDate(first.getDate() - mondayOffset)
  const today = isoDate(new Date())

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    const iso = isoDate(date)
    return {
      key: iso,
      date,
      iso,
      dayNumber: date.getDate(),
      inMonth: date.getMonth() === props.month.getMonth(),
      isToday: iso === today
    }
  })
})

const tripsForDate = (iso: string) => props.trips.filter((trip) => {
  if (!trip.startDate || !trip.endDate) return false
  return iso >= trip.startDate && iso <= trip.endDate
})

const normalizeRange = (end: string) => {
  if (!props.rangeStart || !end) return { start: '', end: '' }
  return props.rangeStart <= end
    ? { start: props.rangeStart, end }
    : { start: end, end: props.rangeStart }
}

const normalizedRange = computed(() =>
  props.rangeComplete ? normalizeRange(props.rangeEnd) : { start: '', end: '' }
)

const normalizedPreviewRange = computed(() =>
  !props.rangeComplete ? normalizeRange(props.rangePreviewEnd || '') : { start: '', end: '' }
)

const isInRange = (iso: string) =>
  Boolean(normalizedRange.value.start && iso >= normalizedRange.value.start && iso <= normalizedRange.value.end)

const isInPreviewRange = (iso: string) =>
  Boolean(
    normalizedPreviewRange.value.start &&
    iso >= normalizedPreviewRange.value.start &&
    iso <= normalizedPreviewRange.value.end
  )

const hoverPointerSelection = (iso: string) => {
  emit('rangeHover', iso)
}

const openContextMenu = (event: MouseEvent, iso: string) => {
  if (!isInRange(iso)) return
  event.preventDefault()
  emit('rangeContext', iso, event.clientX, event.clientY)
}
</script>

<template>
  <div class="travel-calendar">
    <div class="calendar-weekdays" aria-hidden="true">
      <span v-for="weekday in weekdays" :key="weekday">{{ weekday }}</span>
    </div>
    <div class="calendar-grid">
      <div
        v-for="cell in cells"
        :key="cell.key"
        class="calendar-cell"
        :class="{
          'outside-month': !cell.inMonth,
          today: cell.isToday,
          selected: selectedDate === cell.iso,
          'range-selected': isInRange(cell.iso),
          'range-preview': isInPreviewRange(cell.iso),
          'range-start': normalizedRange.start === cell.iso,
          'range-end': normalizedRange.end === cell.iso,
          'range-pending-start': !rangeComplete && rangeStart === cell.iso
        }"
        role="button"
        tabindex="0"
        @pointerenter="hoverPointerSelection(cell.iso)"
        @click="emit('selectDate', cell.iso)"
        @contextmenu="openContextMenu($event, cell.iso)"
        @keydown.enter="emit('selectDate', cell.iso)"
        @keydown.space.prevent="emit('selectDate', cell.iso)"
      >
        <span class="calendar-date-number">{{ cell.dayNumber }}</span>
        <div class="calendar-cell-trips">
          <CalendarTripPill
            v-for="trip in tripsForDate(cell.iso).slice(0, 3)"
            :key="trip.id"
            :trip="trip"
            compact
            @pointerdown.stop
            @open="emit('openTrip', trip, cell.iso)"
          />
          <small v-if="tripsForDate(cell.iso).length > 3">+{{ tripsForDate(cell.iso).length - 3 }}</small>
        </div>
      </div>
    </div>
  </div>
</template>
