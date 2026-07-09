<script setup lang="ts">
import { Check, Landmark, Loader2, MapPin, Plus, RefreshCw, Sparkles, X } from 'lucide-vue-next'
import type { CatalogAttractionResponse, Trip } from '~/types/trip'

const props = defineProps<{
  open: boolean
  trip: Trip
  catalog: CatalogAttractionResponse | null
  loading: boolean
  error: string
  addingCatalogId: string | null
  defaultDayId?: number
}>()

const emit = defineEmits<{
  close: []
  refresh: []
  add: [dayId: number, catalogId: string]
}>()

const selectedDayId = ref<number | null>(props.defaultDayId || props.trip.days[0]?.id || null)

watch(() => props.defaultDayId, (dayId) => {
  if (dayId) selectedDayId.value = dayId
})

watch(() => props.trip.id, () => {
  selectedDayId.value = props.defaultDayId || props.trip.days[0]?.id || null
})

const items = computed(() => props.catalog?.items || [])
const supported = computed(() => props.catalog?.supported ?? true)
const selectedDay = computed(() => props.trip.days.find(day => day.id === selectedDayId.value))

const interestLabel = (interest: string) => ({
  SIGHTSEEING: 'Sehenswürdigkeit',
  CULTURE: 'Kultur',
  NATURE: 'Natur',
  FOOD: 'Food',
  SHOPPING: 'Shopping',
  NIGHTLIFE: 'Nachtleben'
}[interest] || interest)

const compactNumber = (value?: number) => {
  if (value == null || value <= 0) return ''
  return new Intl.NumberFormat('de-DE', { notation: 'compact', maximumFractionDigits: 1 }).format(value)
}

const roundedScore = (score?: number) => score == null ? null : Math.round(score)

const add = (catalogId: string) => {
  if (!selectedDayId.value) return
  emit('add', selectedDayId.value, catalogId)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="catalog-panel">
      <div v-if="open" class="catalog-backdrop" @click.self="$emit('close')">
        <aside class="catalog-panel" aria-label="Aktivitätenkatalog">
          <header class="catalog-header">
            <div>
              <span class="orbit-kicker">Stadtweite Highlights</span>
              <h2>{{ trip.city }} entdecken</h2>
              <p>Bekannte Orte unabhängig von deinem Tagesgebiet.</p>
            </div>
            <button class="catalog-close" type="button" title="Schließen" aria-label="Schließen" @click="$emit('close')">
              <X :size="20" />
            </button>
          </header>

          <div class="catalog-toolbar">
            <label>
              Zu Tag hinzufügen
              <select v-model.number="selectedDayId">
                <option v-for="day in trip.days" :key="day.id" :value="day.id">
                  Tag {{ day.dayNumber }}{{ day.weekday ? ` - ${day.weekday}` : '' }}
                </option>
              </select>
            </label>
            <button class="icon-button" type="button" title="Aktualisieren" :disabled="loading" @click="$emit('refresh')">
              <RefreshCw :size="17" :class="{ spinning: loading }" />
            </button>
          </div>

          <p v-if="error" class="error catalog-error">{{ error }}</p>

          <div v-if="loading" class="catalog-empty">
            <Loader2 class="spinning" :size="28" />
            <strong>Highlights werden geladen</strong>
          </div>

          <div v-else-if="!supported || items.length === 0" class="catalog-empty">
            <Sparkles :size="28" />
            <strong>Noch kein Katalog für diese Stadt</strong>
            <p>{{ catalog?.message || 'Für diese Stadt ist noch kein Highlight-Katalog hinterlegt.' }}</p>
          </div>

          <div v-else class="catalog-list">
            <article
              v-for="item in items"
              :key="item.catalogId"
              class="catalog-item"
              :class="{ planned: item.alreadyPlanned }"
            >
              <div class="catalog-rank">{{ item.rank }}</div>
              <div class="catalog-copy">
                <span class="catalog-meta">
                  <Landmark :size="14" />
                  {{ interestLabel(item.primaryInterest) }}
                </span>
                <h3>{{ item.name }}</h3>
                <p v-if="item.description">{{ item.description }}</p>
                <span v-if="item.publicAttractionScore || item.pageviews" class="catalog-score">
                  <Sparkles :size="14" />
                  <template v-if="roundedScore(item.publicAttractionScore) != null">
                    Score {{ roundedScore(item.publicAttractionScore) }}
                  </template>
                  <template v-if="compactNumber(item.pageviews)">
                    <span v-if="roundedScore(item.publicAttractionScore) != null"> &middot; </span>{{ compactNumber(item.pageviews) }} Aufrufe
                  </template>
                </span>
                <span v-if="item.latitude != null && item.longitude != null" class="catalog-location">
                  <MapPin :size="14" />
                  Koordinaten vorhanden
                </span>
              </div>
              <button
                type="button"
                class="catalog-add"
                :disabled="item.alreadyPlanned || !selectedDay || addingCatalogId === item.catalogId"
                @click="add(item.catalogId)"
              >
                <Check v-if="item.alreadyPlanned" :size="16" />
                <Loader2 v-else-if="addingCatalogId === item.catalogId" class="spinning" :size="16" />
                <Plus v-else :size="16" />
                <span v-if="item.alreadyPlanned">Tag {{ item.plannedDayNumbers.join(', ') }}</span>
                <span v-else>{{ addingCatalogId === item.catalogId ? 'Wird hinzugefügt' : 'Hinzufügen' }}</span>
              </button>
            </article>
          </div>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>
