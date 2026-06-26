<script setup lang="ts">
import { AlertTriangle, CalendarDays, MapPin, Trash2, X } from 'lucide-vue-next'
import type { Trip } from '~/types/trip'

const props = defineProps<{
  open: boolean
  trip: Trip | null
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{
  cancel: []
  confirm: []
}>()

const dialog = ref<HTMLElement | null>(null)
let previousOverflow = ''

const dateLabel = computed(() => {
  if (!props.trip?.startDate || !props.trip?.endDate) return 'Noch ohne festen Zeitraum'
  return `${formatDate(props.trip.startDate)} bis ${formatDate(props.trip.endDate)}`
})

const close = () => {
  if (!props.loading) emit('cancel')
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }

  if (event.key !== 'Tab' || !dialog.value) return
  const focusable = Array.from(
    dialog.value.querySelectorAll<HTMLElement>('button:not(:disabled), [href], [tabindex]:not([tabindex="-1"])')
  )
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(() => props.open, async (open) => {
  if (!import.meta.client) return
  if (open) {
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    await nextTick()
    dialog.value?.querySelector<HTMLElement>('[data-autofocus]')?.focus()
  } else {
    document.body.style.overflow = previousOverflow
  }
})

onBeforeUnmount(() => {
  if (import.meta.client) document.body.style.overflow = previousOverflow
})
</script>

<template>
  <Teleport to="body">
    <Transition name="trip-dialog">
      <div
        v-if="open && trip"
        class="trip-delete-dialog-backdrop"
        role="presentation"
        @mousedown.self="close"
      >
        <section
          ref="dialog"
          class="trip-delete-dialog"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="trip-delete-title"
          aria-describedby="trip-delete-description"
          @keydown="handleKeydown"
        >
          <header class="trip-delete-dialog-header">
            <span class="trip-delete-dialog-icon"><AlertTriangle :size="23" /></span>
            <button
              class="trip-delete-dialog-close"
              type="button"
              aria-label="Dialog schließen"
              :disabled="loading"
              @click="close"
            ><X :size="19" /></button>
          </header>

          <div class="trip-delete-dialog-copy">
            <span class="eyebrow">Reise entfernen</span>
            <h2 id="trip-delete-title">Reise wirklich löschen?</h2>
            <p id="trip-delete-description">
              Der gesamte Reiseplan mit allen Tagen und Aktivitäten wird dauerhaft entfernt.
            </p>
          </div>

          <div class="trip-delete-dialog-summary">
            <strong>{{ trip.city }}</strong>
            <span><MapPin :size="15" />{{ trip.country || trip.state || 'Reiseziel' }}</span>
            <span><CalendarDays :size="15" />{{ dateLabel }}</span>
          </div>

          <p v-if="error" class="trip-delete-dialog-error" role="alert">{{ error }}</p>

          <footer class="trip-delete-dialog-actions">
            <button
              class="secondary"
              type="button"
              data-autofocus
              :disabled="loading"
              @click="close"
            >Abbrechen</button>
            <button
              class="trip-delete-confirm"
              type="button"
              :disabled="loading"
              @click="emit('confirm')"
            >
              <span v-if="loading" class="trip-delete-spinner" aria-hidden="true" />
              <Trash2 v-else :size="17" />
              {{ loading ? 'Wird gelöscht...' : 'Reise löschen' }}
            </button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
