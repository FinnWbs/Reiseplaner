<script setup lang="ts">
import {
  ChevronDown,
  Dumbbell,
  ExternalLink,
  Landmark,
  MapPin,
  Moon,
  Palette,
  RefreshCw,
  ShoppingBag,
  Star,
  Trees,
  Trash2,
  Utensils
} from 'lucide-vue-next'
import type { TripActivity } from '~/types/trip'
import { googleMapsUrl } from '~/utils/maps'

const props = defineProps<{
  item: TripActivity
  city: string
  deleting: boolean
  regenerating: boolean
  selected?: boolean
}>()

defineEmits<{
  remove: [itemId: number]
  regenerate: [itemId: number]
  select: [itemId: number]
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
</script>

<template>
  <article
    class="compact-activity"
    :class="[`category-${categoryName.toLowerCase()}`, { expanded, selected, 'outside-window': !item.fitsAvailability }]"
    tabindex="0"
    @click="$emit('select', item.id)"
    @keydown.enter="$emit('select', item.id)"
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
        @click.stop
      ><ExternalLink :size="17" /></a>
      <button
        class="icon-button"
        type="button"
        :title="expanded ? 'Details schließen' : 'Details anzeigen'"
        @click.stop="expanded = !expanded"
      ><ChevronDown :size="17" /></button>
      <button
        class="icon-button"
        type="button"
        title="Alternative Aktivität"
        :disabled="regenerating"
        @click.stop="$emit('regenerate', item.id)"
      ><RefreshCw :size="17" /></button>
      <button
        class="icon-button"
        type="button"
        title="Aktivität entfernen"
        :disabled="deleting"
        @click.stop="$emit('remove', item.id)"
      ><Trash2 :size="17" /></button>
    </div>
  </article>
</template>
