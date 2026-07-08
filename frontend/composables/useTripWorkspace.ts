import type { CatalogAttractionResponse, Trip, TripDay } from '~/types/trip'

const workspaceErrorMessage = (err: any, fallback: string) =>
  err?.data?.message || err?.data?.error || err?.response?._data?.message || err?.message || fallback

export const useTripWorkspace = () => {
  const { request } = useApi()
  const { clearAuth, hydrateAuth, token, user } = useAuth()
  const trips = ref<Trip[]>([])
  const trip = ref<Trip | null>(null)
  const loading = ref(true)
  const error = ref('')
  const deletingActivityId = ref<number | null>(null)
  const regeneratingActivityId = ref<number | null>(null)
  const deletingTripId = ref<number | null>(null)
  const catalog = ref<CatalogAttractionResponse | null>(null)
  const catalogLoading = ref(false)
  const catalogError = ref('')
  const addingCatalogId = ref<string | null>(null)

  const requireAuth = async () => {
    hydrateAuth()
    if (token.value) return true
    await navigateTo('/auth')
    return false
  }

  const handleLoadError = async (err: any, fallback: string) => {
    error.value = workspaceErrorMessage(err, fallback)
    if (err?.statusCode === 401 || err?.response?.status === 401) {
      clearAuth()
      await navigateTo('/auth')
    }
  }

  const loadTrips = async () => {
    loading.value = true
    error.value = ''
    if (!await requireAuth()) return
    try {
      trips.value = await request<Trip[]>('/trips')
    } catch (err: any) {
      await handleLoadError(err, 'Reisen konnten nicht geladen werden.')
    } finally {
      loading.value = false
    }
  }

  const loadTrip = async (tripId: number) => {
    loading.value = true
    error.value = ''
    if (!await requireAuth()) return
    try {
      trip.value = await request<Trip>(`/trips/${tripId}`)
    } catch (err: any) {
      await handleLoadError(err, 'Reise konnte nicht geladen werden.')
    } finally {
      loading.value = false
    }
  }

  const replaceTrip = (updated: Trip) => {
    trip.value = updated
    trips.value = trips.value.map(item => item.id === updated.id ? updated : item)
  }

  const updateAvailability = async (day: TripDay) => {
    if (!trip.value) return
    error.value = ''
    if (day.availableFrom >= day.availableUntil) {
      day.availableUntil = Math.min(1440, day.availableFrom + 30)
    }
    try {
      replaceTrip(await request<Trip>(`/trips/${trip.value.id}/days/${day.id}/availability`, {
        method: 'PUT',
        body: {
          availableFrom: day.availableFrom,
          availableUntil: day.availableUntil
        }
      }))
    } catch (err: any) {
      error.value = workspaceErrorMessage(err, 'Zeitfenster konnte nicht gespeichert werden.')
      await loadTrip(trip.value.id)
    }
  }

  const removeActivity = async (dayId: number, itemId: number) => {
    if (!trip.value) return
    deletingActivityId.value = itemId
    error.value = ''
    try {
      replaceTrip(await request<Trip>(
        `/trips/${trip.value.id}/days/${dayId}/activities/${itemId}`,
        { method: 'DELETE' }
      ))
    } catch (err: any) {
      error.value = workspaceErrorMessage(err, 'Aktivität konnte nicht entfernt werden.')
    } finally {
      deletingActivityId.value = null
    }
  }

  const regenerateActivity = async (dayId: number, itemId: number) => {
    if (!trip.value) return
    regeneratingActivityId.value = itemId
    error.value = ''
    try {
      replaceTrip(await request<Trip>(
        `/trips/${trip.value.id}/days/${dayId}/activities/${itemId}/regenerate`,
        { method: 'POST' }
      ))
    } catch (err: any) {
      error.value = workspaceErrorMessage(err, 'Keine passende Alternative gefunden.')
    } finally {
      regeneratingActivityId.value = null
    }
  }

  const loadCatalogAttractions = async () => {
    if (!trip.value) return
    catalogLoading.value = true
    catalogError.value = ''
    try {
      catalog.value = await request<CatalogAttractionResponse>(`/trips/${trip.value.id}/catalog-attractions`)
    } catch (err: any) {
      catalogError.value = workspaceErrorMessage(err, 'Highlights konnten nicht geladen werden.')
    } finally {
      catalogLoading.value = false
    }
  }

  const addCatalogAttraction = async (dayId: number, catalogId: string) => {
    if (!trip.value) return
    addingCatalogId.value = catalogId
    catalogError.value = ''
    error.value = ''
    try {
      replaceTrip(await request<Trip>(
        `/trips/${trip.value.id}/days/${dayId}/catalog-attractions/${encodeURIComponent(catalogId)}`,
        { method: 'POST', body: {} }
      ))
      await loadCatalogAttractions()
    } catch (err: any) {
      const message = workspaceErrorMessage(err, 'Highlight konnte nicht hinzugefuegt werden.')
      catalogError.value = message
      error.value = message
    } finally {
      addingCatalogId.value = null
    }
  }

  const deleteTrip = async (tripId: number) => {
    deletingTripId.value = tripId
    error.value = ''
    try {
      await request<void>(`/trips/${tripId}`, { method: 'DELETE' })
      trips.value = trips.value.filter(item => item.id !== tripId)
      if (trip.value?.id === tripId) trip.value = null
      return true
    } catch (err: any) {
      error.value = workspaceErrorMessage(err, 'Reise konnte nicht entfernt werden.')
      return false
    } finally {
      deletingTripId.value = null
    }
  }

  const logout = async () => {
    clearAuth()
    await navigateTo('/auth')
  }

  return {
    trips,
    trip,
    user,
    loading,
    error,
    deletingActivityId,
    regeneratingActivityId,
    deletingTripId,
    catalog,
    catalogLoading,
    catalogError,
    addingCatalogId,
    loadTrips,
    loadTrip,
    loadCatalogAttractions,
    updateAvailability,
    removeActivity,
    regenerateActivity,
    addCatalogAttraction,
    deleteTrip,
    logout
  }
}
