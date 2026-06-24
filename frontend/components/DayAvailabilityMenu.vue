<script setup lang="ts">
import { Check, ChevronDown, Clock3, X } from 'lucide-vue-next'
import type { TripDay } from '~/types/trip'

const props = defineProps<{ day: TripDay }>()
const emit = defineEmits<{ update: [day: TripDay] }>()

const root = ref<HTMLElement | null>(null)
const open = ref(false)
const availableFrom = ref(props.day.availableFrom)
const availableUntil = ref(props.day.availableUntil)

const startOptions = computed(() =>
  Array.from({ length: 48 }, (_, index) => index * 30).filter(value => value < availableUntil.value)
)
const endOptions = computed(() =>
  Array.from({ length: 48 }, (_, index) => (index + 1) * 30).filter(value => value > availableFrom.value)
)

watch(
  () => [props.day.availableFrom, props.day.availableUntil],
  ([from, until]) => {
    availableFrom.value = from
    availableUntil.value = until
  }
)

watch(availableFrom, (from) => {
  if (from >= availableUntil.value) availableUntil.value = Math.min(1440, from + 30)
})

watch(availableUntil, (until) => {
  if (until <= availableFrom.value) availableFrom.value = Math.max(0, until - 30)
})

function close() {
  open.value = false
  availableFrom.value = props.day.availableFrom
  availableUntil.value = props.day.availableUntil
}

function save() {
  emit('update', {
    ...props.day,
    availableFrom: availableFrom.value,
    availableUntil: availableUntil.value
  })
  open.value = false
}

function handleOutsideClick(event: MouseEvent) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) close()
}

onMounted(() => document.addEventListener('click', handleOutsideClick))
onUnmounted(() => document.removeEventListener('click', handleOutsideClick))
</script>

<template>
  <div ref="root" class="day-availability-menu" @click.stop>
    <button
      class="orbit-time-window"
      type="button"
      :aria-expanded="open"
      aria-haspopup="dialog"
      title="Zeitfenster ändern"
      @click="open = !open"
    >
      <Clock3 :size="15" />
      <span>{{ formatMinutes(day.availableFrom) }}</span>
      <span aria-hidden="true">–</span>
      <span>{{ formatMinutes(day.availableUntil) }}</span>
      <ChevronDown :size="14" />
    </button>

    <div v-if="open" class="day-availability-popover" role="dialog" aria-label="Zeitfenster ändern">
      <div class="day-availability-fields">
        <label>
          <span>Von</span>
          <select v-model.number="availableFrom">
            <option v-for="value in startOptions" :key="value" :value="value">{{ formatMinutes(value) }}</option>
          </select>
        </label>
        <label>
          <span>Bis</span>
          <select v-model.number="availableUntil">
            <option v-for="value in endOptions" :key="value" :value="value">{{ formatMinutes(value) }}</option>
          </select>
        </label>
      </div>
      <div class="day-availability-actions">
        <button class="icon-button secondary" type="button" title="Abbrechen" @click="close"><X :size="16" /></button>
        <button class="compact-button" type="button" @click="save"><Check :size="16" />Übernehmen</button>
      </div>
    </div>
  </div>
</template>
