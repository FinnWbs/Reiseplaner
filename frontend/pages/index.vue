<script setup lang="ts">
import {
  Dumbbell,
  Landmark,
  MapPin,
  Moon,
  Palette,
  ShoppingBag,
  Star,
  Trees,
  Trash2,
  Utensils
} from 'lucide-vue-next'

type Interest = { id: number; name: string }
type Activity = {
  id: number
  name: string
  description?: string
  city: string
  category?: string
  subcategory?: string
  address?: string
  rating?: number
}
type ActivityImportResponse = {
  city: string
  createdCount: number
  updatedCount: number
  skippedCount: number
  activities: Activity[]
  warnings: string[]
}
type Trip = {
  id: number
  city: string
  daysCount: number
  status: string
  days: Array<{
    id: number
    dayNumber: number
    activities: Array<{
      id: number
      position: number
      locked: boolean
      notes?: string
      activity: {
        id: number
        name: string
        description?: string
        category?: string
        subcategory?: string
        address?: string
        rating?: number
        dataQualityScore: number
      }
    }>
  }>
}

const { request } = useApi()
const { clearAuth, hydrateAuth, token, user } = useAuth()
const city = ref('Berlin')
const daysCount = ref(3)
const interests = ref<Interest[]>([])
const selectedInterestIds = ref<number[]>([])
const trips = ref<Trip[]>([])
const activeTrip = ref<Trip | null>(null)
const error = ref('')
const loading = ref(false)
const activities = ref<Activity[]>([])
const activityError = ref('')
const activityLoading = ref<'load' | 'import' | null>(null)
const importSummary = ref('')
const importWarnings = ref<string[]>([])
const tripError = ref('')
const deletingActivityId = ref<number | null>(null)

const isLoggedIn = computed(() => Boolean(token.value))

const suggestedTime = (position: number) => {
  const times = ['10:00', '14:30']
  return times[position - 1] || `${10 + (position - 1) * 3}:00`
}

const suggestedDuration = (category?: string) => {
  const durations: Record<string, string> = {
    Kultur: 'ca. 2 Std.',
    Geschichte: 'ca. 1,5 Std.',
    Natur: 'ca. 2 Std.',
    Food: 'ca. 1,5 Std.',
    Shopping: 'ca. 2 Std.',
    Nightlife: 'ca. 3 Std.',
    Sport: 'ca. 2 Std.'
  }
  return durations[category || ''] || 'ca. 1,5 Std.'
}

const categoryIcon = (category?: string) => {
  const icons: Record<string, typeof Palette> = {
    Kultur: Palette,
    Geschichte: Landmark,
    Natur: Trees,
    Food: Utensils,
    Shopping: ShoppingBag,
    Nightlife: Moon,
    Sport: Dumbbell
  }
  return icons[category || ''] || Landmark
}

const loadInitialData = async () => {
  interests.value = await request<Interest[]>('/interests')
  trips.value = await request<Trip[]>('/trips')
  if (trips.value.length > 0) {
    activeTrip.value = trips.value[0]
  }
}

const toggleInterest = (id: number) => {
  selectedInterestIds.value = selectedInterestIds.value.includes(id)
    ? selectedInterestIds.value.filter((item) => item !== id)
    : [...selectedInterestIds.value, id]
}

const createTrip = async () => {
  error.value = ''
  loading.value = true
  try {
    activeTrip.value = await request<Trip>('/trips', {
      method: 'POST',
      body: {
        city: city.value,
        daysCount: daysCount.value,
        interestIds: selectedInterestIds.value
      }
    })
    trips.value = await request<Trip[]>('/trips')
  } catch (err: any) {
    error.value = err?.data?.message || 'Reise konnte nicht erstellt werden.'
  } finally {
    loading.value = false
  }
}

const loadActivities = async () => {
  activityError.value = ''
  importSummary.value = ''
  importWarnings.value = []
  activityLoading.value = 'load'
  try {
    activities.value = await request<Activity[]>(`/activities?city=${encodeURIComponent(city.value)}`)
  } catch (err: any) {
    activityError.value = err?.data?.message || 'Gespeicherte Aktivitaeten konnten nicht geladen werden.'
  } finally {
    activityLoading.value = null
  }
}

const importActivities = async () => {
  activityError.value = ''
  importSummary.value = ''
  importWarnings.value = []
  activityLoading.value = 'import'
  try {
    const result = await request<ActivityImportResponse>(`/activities/import?city=${encodeURIComponent(city.value)}`, {
      method: 'POST'
    })
    activities.value = result.activities
    importWarnings.value = result.warnings
    importSummary.value = `${result.createdCount} neu, ${result.updatedCount} aktualisiert, ${result.skippedCount} uebersprungen.`
  } catch (err: any) {
    activityError.value = err?.data?.message || 'Aktivitaeten konnten nicht importiert werden.'
  } finally {
    activityLoading.value = null
  }
}

const removeActivity = async (dayId: number, itemId: number) => {
  if (!activeTrip.value) return
  tripError.value = ''
  deletingActivityId.value = itemId
  try {
    const updatedTrip = await request<Trip>(
      `/trips/${activeTrip.value.id}/days/${dayId}/activities/${itemId}`,
      { method: 'DELETE' }
    )
    activeTrip.value = updatedTrip
    trips.value = trips.value.map((trip) => trip.id === updatedTrip.id ? updatedTrip : trip)
  } catch (err: any) {
    tripError.value = err?.data?.message || 'Aktivitaet konnte nicht entfernt werden.'
  } finally {
    deletingActivityId.value = null
  }
}

const deleteTrip = async (tripId: number) => {
  error.value = ''
  try {
    await request<void>(`/trips/${tripId}`, { method: 'DELETE' })
    trips.value = trips.value.filter((trip) => trip.id !== tripId)
    if (activeTrip.value?.id === tripId) {
      activeTrip.value = trips.value[0] ?? null
    }
  } catch (err: any) {
    error.value = err?.data?.message || 'Reise konnte nicht geloescht werden.'
  }
}

const logout = async () => {
  clearAuth()
  await navigateTo('/auth')
}

onMounted(async () => {
  hydrateAuth()

  if (!token.value) {
    await navigateTo('/auth')
    return
  }

  try {
    await loadInitialData()
  } catch (err: any) {
    error.value = err?.data?.message || 'Daten konnten nicht geladen werden.'
    if (err?.statusCode === 401 || err?.response?.status === 401) {
      clearAuth()
      await navigateTo('/auth')
    }
  }
})
</script>

<template>
  <div class="page planner-page">
    <main class="main">
      <section v-if="!isLoggedIn" class="panel grid">
        <h2>Anmeldung erforderlich</h2>
        <p class="muted">Melde dich an, um Reisen zu erstellen und gespeicherte Plaene zu sehen.</p>
        <div class="actions">
          <NuxtLink class="button-link" to="/auth">Zum Login</NuxtLink>
        </div>
      </section>

      <template v-else>
        <section class="panel toolbar">
          <div>
            <strong>{{ user?.displayName || user?.email }}</strong>
            <p class="muted">Angemeldet als {{ user?.email }}</p>
          </div>
          <button class="secondary" @click="logout">Abmelden</button>
        </section>

        <section class="panel grid">
          <h2>Reise erstellen</h2>
          <div class="columns">
            <label>
              Stadt
              <input v-model="city" type="text">
            </label>
            <label>
              Reisetage
              <input v-model.number="daysCount" type="number" min="1" max="14">
            </label>
          </div>
          <div class="grid">
            <strong>Interessen</strong>
            <div class="interests">
              <button
                v-for="interest in interests"
                :key="interest.id"
                class="chip"
                :class="{ active: selectedInterestIds.includes(interest.id) }"
                @click="toggleInterest(interest.id)"
              >
                {{ interest.name }}
              </button>
            </div>
          </div>
          <p v-if="error" class="error">{{ error }}</p>
          <button :disabled="loading || selectedInterestIds.length === 0" @click="createTrip">Plan generieren</button>
        </section>

        <section class="panel grid">
          <div>
            <h2>Aktivitaeten in {{ city || 'der Stadt' }}</h2>
            <p class="muted">Gespeicherte Orte laden oder neue Daten aus Geoapify und Wikimedia importieren.</p>
          </div>
          <div class="actions">
            <button
              class="secondary"
              :disabled="Boolean(activityLoading) || !city.trim()"
              @click="loadActivities"
            >
              {{ activityLoading === 'load' ? 'Wird geladen...' : 'Gespeicherte laden' }}
            </button>
            <button :disabled="Boolean(activityLoading) || !city.trim()" @click="importActivities">
              {{ activityLoading === 'import' ? 'Import laeuft...' : 'Aktivitaeten importieren' }}
            </button>
          </div>
          <p v-if="activityError" class="error">{{ activityError }}</p>
          <p v-if="importSummary" class="success">{{ importSummary }}</p>
          <ul v-if="importWarnings.length" class="warnings">
            <li v-for="warning in importWarnings" :key="warning">{{ warning }}</li>
          </ul>
          <p v-if="!activityLoading && activities.length === 0" class="muted">
            Fuer diese Stadt wurden noch keine Aktivitaeten geladen.
          </p>
          <div v-else class="activity-grid">
            <article v-for="activity in activities" :key="activity.id" class="activity-card">
              <div class="activity-card-header">
                <strong>{{ activity.name }}</strong>
                <span class="category">{{ activity.category || 'Ohne Kategorie' }}</span>
              </div>
              <span v-if="activity.subcategory" class="muted">{{ activity.subcategory }}</span>
              <p v-if="activity.description">{{ activity.description }}</p>
              <span v-if="activity.address" class="muted">{{ activity.address }}</span>
              <span v-if="activity.rating != null" class="muted">Rating {{ activity.rating }}</span>
            </article>
          </div>
        </section>

        <section v-if="activeTrip" class="panel grid">
          <div class="trip-plan-heading">
            <div>
              <span class="eyebrow">Dein Reiseplan</span>
              <h2>{{ activeTrip.city }} &middot; {{ activeTrip.daysCount }} Tage</h2>
            </div>
            <span class="muted">Vorschlagszeiten, keine geprueften Oeffnungszeiten</span>
          </div>
          <p v-if="tripError" class="error">{{ tripError }}</p>
          <div v-for="day in activeTrip.days" :key="day.id" class="trip-day">
            <div class="trip-day-heading">
              <div class="day-number">{{ day.dayNumber }}</div>
              <div>
                <h3>Tag {{ day.dayNumber }}</h3>
                <span class="muted">
                  {{ day.activities.length }} {{ day.activities.length === 1 ? 'Aktivitaet' : 'Aktivitaeten' }}
                </span>
              </div>
            </div>
            <p v-if="day.activities.length === 0" class="muted">Noch keine Aktivitaeten geplant.</p>
            <div v-else class="day-schedule">
              <article v-for="item in day.activities" :key="item.id" class="schedule-item">
                <div class="schedule-time">
                  <strong>{{ suggestedTime(item.position) }}</strong>
                  <span>{{ suggestedDuration(item.activity.category) }}</span>
                </div>
                <div class="schedule-marker" aria-hidden="true">
                  <span />
                </div>
                <div class="schedule-content">
                  <div class="activity-icon" :title="item.activity.category || 'Aktivitaet'">
                    <component :is="categoryIcon(item.activity.category)" :size="22" :stroke-width="1.8" />
                  </div>
                  <div class="schedule-main">
                    <div class="schedule-header">
                      <div>
                        <span class="schedule-position">
                          {{ item.activity.category || 'Aktivitaet' }} &middot; Stopp {{ item.position }}
                        </span>
                        <h4>{{ item.activity.name }}</h4>
                      </div>
                    </div>
                    <p v-if="item.activity.description" class="schedule-description">
                      {{ item.activity.description }}
                    </p>
                    <div class="activity-facts">
                      <span v-if="item.activity.rating != null">
                        <Star :size="15" :stroke-width="2" />
                        {{ item.activity.rating.toFixed(1) }}
                      </span>
                      <span v-if="item.activity.address">
                        <MapPin :size="15" :stroke-width="2" />
                        {{ item.activity.address }}
                      </span>
                    </div>
                  </div>
                  <div class="schedule-actions">
                    <button
                      type="button"
                      class="icon-button"
                      title="Aktivitaet entfernen"
                      :aria-label="`${item.activity.name} entfernen`"
                      :disabled="deletingActivityId === item.id"
                      @click.stop.prevent="removeActivity(day.id, item.id)"
                    >
                      <Trash2 :size="18" :stroke-width="2" />
                    </button>
                  </div>
                </div>
              </article>
            </div>
          </div>
        </section>

        <section v-if="trips.length > 0" class="panel grid">
          <h2>Gespeicherte Reisen</h2>
          <div class="trip-list">
            <div v-for="trip in trips" :key="trip.id" class="trip-list-item">
              <button class="secondary trip-select" @click="activeTrip = trip">
                {{ trip.city }} &middot; {{ trip.daysCount }} Tage &middot; {{ trip.status }}
              </button>
              <button class="danger" :aria-label="`Reise ${trip.city} loeschen`" @click="deleteTrip(trip.id)">
                Loeschen
              </button>
            </div>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>
