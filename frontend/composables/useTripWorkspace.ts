import type { Trip, TripActivity, TripDay } from '~/types/trip'
import { useTripCatalogActions } from '~/composables/useTripCatalogActions'
import { useTripImages } from '~/composables/useTripImages'
import { workspaceErrorMessage } from '~/utils/workspaceErrors'

export const useTripWorkspace = () => {
  const { request } = useApi()
  const config = useRuntimeConfig()
  const { clearAuth, hydrateAuth, token, user } = useAuth()
  const trips = ref<Trip[]>([])
  const trip = ref<Trip | null>(null)
  const loading = ref(true)
  const error = ref('')
  const deletingActivityId = ref<number | null>(null)
  const regeneratingActivityId = ref<number | null>(null)
  const deletingTripId = ref<number | null>(null)
  const savingSchedule = ref(false)
  const tripImages = useTripImages(trip, request, config)

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

  const replaceTrip = (updated: Trip) => {
    const merged = tripImages.mergeTripPreservingImages(updated)
    trip.value = merged
    trips.value = trips.value.map(item => item.id === merged.id ? merged : item)
  }

  const catalogActions = useTripCatalogActions(trip, request, replaceTrip, error)

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
      error.value = workspaceErrorMessage(err, 'Aktivitaet konnte nicht entfernt werden.')
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

  const normalizeClientSchedule = () => {
    if (!trip.value) return
    for (const day of trip.value.days) {
      day.activities.forEach((item, index) => {
        item.position = index + 1
      })
    }
  }

  const persistSchedule = async (options: { includeTimings?: boolean } = {}) => {
    if (!trip.value || savingSchedule.value) return
    const tripId = trip.value.id
    savingSchedule.value = true
    error.value = ''
    try {
      replaceTrip(await request<Trip>(`/trips/${tripId}/schedule`, {
        method: 'PUT',
        body: {
          days: trip.value.days.map(day => ({
            dayId: day.id,
            activityItemIds: day.activities.map(item => item.id),
            ...(options.includeTimings
              ? {
                  activities: day.activities.map(item => ({
                    itemId: item.id,
                    scheduledStart: item.scheduledStart,
                    durationMinutes: item.durationMinutes
                  }))
                }
              : {})
          }))
        }
      }))
    } catch (err: any) {
      error.value = workspaceErrorMessage(err, 'Der Zeitplan konnte nicht gespeichert werden.')
      await loadTrip(tripId)
    } finally {
      savingSchedule.value = false
    }
  }

  const reorderActivities = async (dayId: number, activityItemIds: number[]) => {
    if (!trip.value || savingSchedule.value) return
    const day = trip.value.days.find(item => item.id === dayId)
    if (!day) return
    const activityById = new Map(day.activities.map(item => [item.id, item]))
    const reordered = activityItemIds
      .map(itemId => activityById.get(itemId))
      .filter((item): item is TripActivity => Boolean(item))
    if (reordered.length !== day.activities.length) return
    day.activities = reordered
    normalizeClientSchedule()
    await persistSchedule()
  }

  const updateActivityTiming = async (
    dayId: number,
    itemId: number,
    scheduledStart: number,
    durationMinutes: number
  ) => {
    if (!trip.value || savingSchedule.value) return
    const day = trip.value.days.find(item => item.id === dayId)
    const activity = day?.activities.find(item => item.id === itemId)
    if (!day || !activity) return
    activity.scheduledStart = scheduledStart
    activity.durationMinutes = durationMinutes
    await persistSchedule({ includeTimings: true })
  }

  const moveActivityToDay = async (sourceDayId: number, itemId: number, targetDayId: number) => {
    if (!trip.value || savingSchedule.value || sourceDayId === targetDayId) return
    const sourceDay = trip.value.days.find(day => day.id === sourceDayId)
    const targetDay = trip.value.days.find(day => day.id === targetDayId)
    if (!sourceDay || !targetDay) return
    const movingItem = sourceDay.activities.find(item => item.id === itemId)
    if (!movingItem) return
    sourceDay.activities = sourceDay.activities.filter(item => item.id !== itemId)
    targetDay.activities = [...targetDay.activities, movingItem]
    normalizeClientSchedule()
    await persistSchedule()
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
    savingSchedule,
    catalog: catalogActions.catalog,
    catalogLoading: catalogActions.catalogLoading,
    catalogError: catalogActions.catalogError,
    addingCatalogId: catalogActions.addingCatalogId,
    imagePreloadRunning: tripImages.imagePreloadRunning,
    imagePreloadStatus: tripImages.imagePreloadStatus,
    ensureActivityImages: tripImages.ensureActivityImages,
    loadTrips,
    loadTrip,
    loadCatalogAttractions: catalogActions.loadCatalogAttractions,
    updateAvailability,
    removeActivity,
    regenerateActivity,
    reorderActivities,
    updateActivityTiming,
    moveActivityToDay,
    addCatalogAttraction: catalogActions.addCatalogAttraction,
    deleteTrip,
    preloadUpcomingImages: tripImages.preloadUpcomingImages,
    logout
  }
}
