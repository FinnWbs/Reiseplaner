<script setup lang="ts">
type Interest = { id: number; name: string }
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
        description: string
        category: string
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

const isLoggedIn = computed(() => Boolean(token.value))

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

const removeActivity = async (dayId: number, itemId: number) => {
  if (!activeTrip.value) return
  activeTrip.value = await request<Trip>(`/trips/${activeTrip.value.id}/days/${dayId}/activities/${itemId}`, {
    method: 'DELETE'
  })
  trips.value = await request<Trip[]>('/trips')
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
  <div class="page">
    <aside class="sidebar">
      <h1>TravelMate Planner</h1>
      <p>Ein kompaktes MVP fuer personalisierte Staedtereisen: Interessen waehlen, Stadt eintragen, Reiseplan generieren.</p>
      <p class="muted">Regelbasiert, lokal gespeichert, ohne KI und ohne Routing-Komplexitaet.</p>
    </aside>

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

        <section v-if="activeTrip" class="panel grid">
          <h2>{{ activeTrip.city }} &middot; {{ activeTrip.daysCount }} Tage</h2>
          <div v-for="day in activeTrip.days" :key="day.id" class="trip-day">
            <h3>Tag {{ day.dayNumber }}</h3>
            <p v-if="day.activities.length === 0" class="muted">Noch keine Aktivitaeten geplant.</p>
            <article v-for="item in day.activities" :key="item.id" class="activity">
              <strong>{{ item.position }}. {{ item.activity.name }}</strong>
              <span class="muted">{{ item.activity.category }} &middot; Rating {{ item.activity.rating ?? 'n/a' }}</span>
              <span>{{ item.activity.description }}</span>
              <div class="actions">
                <button class="secondary" @click="removeActivity(day.id, item.id)">Entfernen</button>
              </div>
            </article>
          </div>
        </section>

        <section v-if="trips.length > 1" class="panel grid">
          <h2>Gespeicherte Reisen</h2>
          <button v-for="trip in trips" :key="trip.id" class="secondary" @click="activeTrip = trip">
            {{ trip.city }} &middot; {{ trip.daysCount }} Tage &middot; {{ trip.status }}
          </button>
        </section>
      </template>
    </main>
  </div>
</template>
