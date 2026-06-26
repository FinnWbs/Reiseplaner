<script setup lang="ts">
import {
  ChevronDown,
  Dumbbell,
  ExternalLink,
  Landmark,
  MapPin,
  Moon,
  Palette,
  ShoppingBag,
  Star,
  Trees,
  Utensils
} from 'lucide-vue-next'
import type { TripActivity } from '~/types/trip'
import { googleMapsUrl } from '~/utils/maps'

const props = defineProps<{
  item: TripActivity
  city: string
  selected?: boolean
}>()

const emit = defineEmits<{
  select: [itemId: number]
  actionMenu: [itemId: number, x: number, y: number]
}>()

const expanded = ref(false)

const categoryName = computed(() => {
  const value = `${props.item.activity.category || ''} ${props.item.activity.subcategory || ''}`.toLowerCase()
  if (/night|club|bar|pub/.test(value)) return 'Nightlife'
  if (/food|restaurant|cafe|market|catering/.test(value)) return 'Food'
  if (/park|natur|garden|forest|beach/.test(value)) return 'Natur'
  if (/shop|commercial|mall/.test(value)) return 'Shopping'
  if (/sport|stadium|fitness/.test(value)) return 'Sport'
  if (/heritage|historic|monument|castle|geschichte/.test(value)) return 'Geschichte'
  return 'Kultur'
})

const icon = computed(() => ({
  Kultur: Palette,
  Geschichte: Landmark,
  Natur: Trees,
  Food: Utensils,
  Shopping: ShoppingBag,
  Nightlife: Moon,
  Sport: Dumbbell
})[categoryName.value])

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
    :class="[`category-${categoryName.toLowerCase()}`, { expanded, selected, 'outside-window': !item.fitsAvailability }]"
    tabindex="0"
    @click="emit('select', item.id)"
    @contextmenu="openActionMenu"
    @keydown.enter="emit('select', item.id)"
    @keydown="openKeyboardActionMenu"
  >
    <div class="compact-activity-time">
      <strong>{{ formatMinutes(item.scheduledStart) }}</strong>
      <span>{{ item.durationMinutes }} Min.</span>
    </div>
    <span class="compact-activity-line" aria-hidden="true"><i /></span>
    <div class="compact-activity-icon">
      <component :is="icon" :size="20" :stroke-width="1.8" />
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
