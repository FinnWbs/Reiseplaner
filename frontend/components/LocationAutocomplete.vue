<script setup lang="ts">
import { MapPin } from 'lucide-vue-next'
import type { LocationSuggestion } from '~/types/trip'

defineProps<{
  suggestions: LocationSuggestion[]
  selectedLocation: LocationSuggestion | null
  loading: boolean
  error: string
  dropdownOpen: boolean
  highlightedIndex: number
  describeLocation: (suggestion: LocationSuggestion) => string
}>()

const city = defineModel<string>('city', { required: true })

defineEmits<{
  search: []
  focusDropdown: []
  keydown: [event: KeyboardEvent]
  select: [suggestion: LocationSuggestion]
  highlight: [index: number]
}>()
</script>

<template>
  <label class="location-search">
    Reiseziel
    <div class="location-input-wrap">
      <input
        v-model="city"
        type="text"
        placeholder="z. B. Berlin"
        autocomplete="off"
        @input="$emit('search')"
        @focus="$emit('focusDropdown')"
        @keydown="$emit('keydown', $event)"
      >
      <span v-if="loading" class="location-loading">Sucht...</span>
    </div>
    <div v-if="dropdownOpen" class="location-dropdown">
      <button
        v-for="(suggestion, index) in suggestions"
        :key="suggestion.id"
        type="button"
        class="location-option"
        :class="{ highlighted: highlightedIndex === index }"
        @mousedown.prevent="$emit('select', suggestion)"
        @mouseenter="$emit('highlight', index)"
      >
        <MapPin :size="18" />
        <span>
          <strong>{{ suggestion.city }}</strong>
          <small>{{ describeLocation(suggestion) }}</small>
        </span>
      </button>
    </div>
    <span v-if="selectedLocation" class="selected-location">
      Ausgewaehlt: {{ selectedLocation.city }}<template v-if="describeLocation(selectedLocation)">, {{ describeLocation(selectedLocation) }}</template>
    </span>
    <span v-else-if="error" class="field-error">{{ error }}</span>
  </label>
</template>
