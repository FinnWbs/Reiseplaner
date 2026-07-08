import type { ActivityImage, CatalogAttractionResponse, Trip, TripActivity, TripDay } from '~/types/trip'

type ImagePreloadStatus = 'idle' | 'loading' | 'ready' | 'failed'

const workspaceErrorMessage = (err: any, fallback: string) =>
  err?.data?.message || err?.data?.error || err?.response?._data?.message || err?.message || fallback

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
  const catalog = ref<CatalogAttractionResponse | null>(null)
  const catalogLoading = ref(false)
  const catalogError = ref('')
  const addingCatalogId = ref<string | null>(null)
  const imagePreloadRunning = ref(false)
  const imagePreloadQueue = new Set<number>()
  const imageEnrichmentInFlight = new Map<number, Promise<void>>()
  const imagePreloadDone = new Set<number>()
  const imagePreloadFailedAt = new Map<number, number>()
  const warmedImageUrls = new Set<string>()
  const warmingImageUrls = new Set<string>()
  const imagePreloadStatus = reactive<Record<number, ImagePreloadStatus>>({})
  let imagePreloadRunId = 0
  const imageRetryAfterMs = 120_000
  const imageWarmTimeoutMs = 10_000

  const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

  const debugImages = (event: string, payload: Record<string, unknown> = {}) => {
    if (!import.meta.dev) return
    console.debug('[TripImages]', event, payload)
  }

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

  const resolveImageUrl = (url: string) => {
    if (!url) return ''
    if (/^https?:\/\//i.test(url)) return url
    const base = String(config.public.apiBase || '').replace(/\/$/, '')
    return `${base}${url.startsWith('/') ? url : `/${url}`}`
  }

  const isUsableImageUrl = (url: string) => {
    if (!url) return false
    if (url.includes('/undefined/') || url.includes('/null/')) return false
    return /^https?:\/\//i.test(url) || url.startsWith('/')
  }

  const usableImages = (images: ActivityImage[] | undefined | null) =>
    Array.isArray(images)
      ? images.filter(image => image && isUsableImageUrl(resolveImageUrl(image.url)))
      : []

  const mergeImages = (...imageLists: Array<ActivityImage[] | undefined | null>) => {
    const merged: ActivityImage[] = []
    const seen = new Set<string>()
    for (const image of imageLists.flatMap(list => usableImages(list))) {
      const url = resolveImageUrl(image.url)
      if (seen.has(url)) continue
      seen.add(url)
      merged.push(image)
    }
    return merged
  }

  const collectImageCounts = (source: Trip | null | undefined) => {
    const counts: Record<number, number> = {}
    for (const day of source?.days || []) {
      for (const item of day.activities || []) {
        counts[item.activity.id] = usableImages(item.activity.images).length
      }
    }
    return counts
  }

  const collectImagesByActivity = (source: Trip | null | undefined) => {
    const imagesByActivity = new Map<number, ActivityImage[]>()
    for (const day of source?.days || []) {
      for (const item of day.activities || []) {
        const existing = imagesByActivity.get(item.activity.id)
        imagesByActivity.set(
          item.activity.id,
          mergeImages(existing, item.activity.images)
        )
      }
    }
    return imagesByActivity
  }

  const mergeTripPreservingImages = (updated: Trip) => {
    const currentImages = collectImagesByActivity(trip.value)
    for (const day of updated.days || []) {
      for (const item of day.activities || []) {
        const oldImages = currentImages.get(item.activity.id) || []
        const newImages = usableImages(item.activity.images)
        const merged = newImages.length
          ? mergeImages(oldImages, newImages)
          : oldImages
        if (merged.length) item.activity.images = merged
      }
    }
    return updated
  }

  const replaceTrip = (updated: Trip) => {
    const before = collectImageCounts(trip.value)
    const merged = mergeTripPreservingImages(updated)
    const after = collectImageCounts(merged)
    debugImages('replace-trip', { tripId: updated.id, before, after })
    trip.value = merged
    trips.value = trips.value.map(item => item.id === merged.id ? merged : item)
  }

  const setImageStatus = (activityId: number, status: ImagePreloadStatus) => {
    imagePreloadStatus[activityId] = status
  }

  const updateActivityImages = (activityId: number, images: ActivityImage[]) => {
    if (!trip.value) return
    if (!images.length) return
    for (const day of trip.value.days) {
      for (const item of day.activities) {
        if (item.activity.id === activityId) {
          const before = usableImages(item.activity.images).length
          item.activity.images = mergeImages(item.activity.images, images)
          debugImages('merge-activity-images', {
            activityId,
            before,
            incoming: usableImages(images).length,
            after: usableImages(item.activity.images).length
          })
        }
      }
    }
  }

  const findTripActivity = (activityId: number) => {
    for (const day of trip.value?.days || []) {
      const item = day.activities.find(activity => activity.activity.id === activityId)
      if (item) return item
    }
    return null
  }

  const warmImageUrl = (url: string) => new Promise<boolean>((resolve) => {
    if (!import.meta.client) {
      resolve(false)
      return
    }
    const resolved = resolveImageUrl(url)
    if (!isUsableImageUrl(resolved)) {
      resolve(false)
      return
    }
    if (warmedImageUrls.has(resolved)) {
      resolve(true)
      return
    }
    if (warmingImageUrls.has(resolved)) {
      resolve(true)
      return
    }
    warmingImageUrls.add(resolved)
    const image = new Image()
    let settled = false
    const finish = (loaded: boolean) => {
      if (settled) return
      settled = true
      window.clearTimeout(timeoutId)
      warmingImageUrls.delete(resolved)
      if (loaded) warmedImageUrls.add(resolved)
      resolve(loaded)
    }
    const timeoutId = window.setTimeout(() => finish(false), imageWarmTimeoutMs)
    image.onload = () => finish(true)
    image.onerror = () => finish(false)
    image.decoding = 'async'
    image.loading = 'eager'
    image.src = resolved
  })

  const warmActivityImages = async (images: ActivityImage[]) => {
    const candidates = usableImages(images).slice(0, 3)
    if (!candidates.length) return false
    const results = await Promise.all(candidates.map(image => warmImageUrl(image.url)))
    return results.some(Boolean)
  }

  const enrichActivityImages = async (item: TripActivity, reason = 'unknown') => {
    const activityId = item.activity.id
    const existingImages = usableImages(item.activity.images)
    debugImages('enrich-start', {
      activityId,
      reason,
      existingImages: existingImages.length
    })
    if (existingImages.length) {
      setImageStatus(activityId, 'loading')
      const warmed = await warmActivityImages(existingImages)
      if (warmed) {
        imagePreloadDone.add(activityId)
        imagePreloadFailedAt.delete(activityId)
        setImageStatus(activityId, 'ready')
        debugImages('enrich-existing-ready', { activityId, reason, existingImages: existingImages.length })
      } else {
        imagePreloadFailedAt.set(activityId, Date.now())
        setImageStatus(activityId, 'failed')
        debugImages('enrich-existing-failed', { activityId, reason, existingImages: existingImages.length })
      }
      return
    }
    if (imagePreloadDone.has(activityId) || imagePreloadQueue.has(activityId)) {
      debugImages('enrich-skip-done-or-queued', { activityId, reason })
      return
    }
    const lastFailure = imagePreloadFailedAt.get(activityId)
    const isVisibleRequest = reason === 'visible-gallery'
    if (!isVisibleRequest && lastFailure && Date.now() - lastFailure < imageRetryAfterMs) {
      debugImages('enrich-skip-retry-cooldown', {
        activityId,
        reason,
        retryInMs: imageRetryAfterMs - (Date.now() - lastFailure)
      })
      return
    }
    if (isVisibleRequest && lastFailure) {
      imagePreloadFailedAt.delete(activityId)
      debugImages('enrich-visible-override-cooldown', {
        activityId,
        reason,
        previousFailureAgeMs: Date.now() - lastFailure
      })
    }

    imagePreloadQueue.add(activityId)
    setImageStatus(activityId, 'loading')
    try {
      const images = await request<ActivityImage[]>(`/activities/${activityId}/images`, {
        method: 'POST'
      })
      const safeImages = mergeImages(existingImages, images)
      if (safeImages.length) {
        updateActivityImages(activityId, safeImages)
        const warmed = await warmActivityImages(safeImages)
        if (warmed) {
          imagePreloadDone.add(activityId)
          imagePreloadFailedAt.delete(activityId)
          setImageStatus(activityId, 'ready')
          debugImages('enrich-ready', {
            activityId,
            reason,
            received: usableImages(images).length,
            safeImages: safeImages.length
          })
        } else {
          imagePreloadFailedAt.set(activityId, Date.now())
          setImageStatus(activityId, 'failed')
          debugImages('enrich-warm-failed', {
            activityId,
            reason,
            received: usableImages(images).length,
            safeImages: safeImages.length
          })
        }
      } else {
        imagePreloadFailedAt.set(activityId, Date.now())
        setImageStatus(activityId, 'failed')
        debugImages('enrich-no-images', { activityId, reason })
      }
    } catch {
      imagePreloadFailedAt.set(activityId, Date.now())
      setImageStatus(activityId, 'failed')
      debugImages('enrich-error', { activityId, reason })
      // Bilder sind optional: Reise und Tagesplanung bleiben nutzbar.
    } finally {
      imagePreloadQueue.delete(activityId)
      debugImages('enrich-end', { activityId, reason, status: imagePreloadStatus[activityId] })
    }
  }

  const ensureActivityImages = async (activityId: number, reason = 'visible-gallery') => {
    const item = findTripActivity(activityId)
    if (!item) {
      debugImages('ensure-skip-missing-activity', { activityId, reason })
      return
    }
    const inFlight = imageEnrichmentInFlight.get(activityId)
    if (inFlight) {
      debugImages('ensure-join-inflight', { activityId, reason })
      await inFlight
      return
    }
    const task = enrichActivityImages(item, reason)
      .finally(() => {
        imageEnrichmentInFlight.delete(activityId)
      })
    imageEnrichmentInFlight.set(activityId, task)
    await task
  }

  const preloadUpcomingImages = async (activeIndex = 0, daysAhead = 2) => {
    const currentTrip = trip.value
    if (!currentTrip?.days?.length) return
    const runId = ++imagePreloadRunId
    const start = Math.max(0, activeIndex)
    const end = Math.min(currentTrip.days.length - 1, start + daysAhead)
    const activities = currentTrip.days
      .slice(start, end + 1)
      .flatMap(day => day.activities || [])
      .filter(item => item?.activity?.id)

    if (!activities.length) return
    imagePreloadRunning.value = true
    try {
      for (const item of activities) {
        if (runId !== imagePreloadRunId || trip.value?.id !== currentTrip.id) return
        await ensureActivityImages(item.activity.id, 'preloader')
        await wait(300)
      }
    } finally {
      if (runId === imagePreloadRunId) imagePreloadRunning.value = false
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
    imagePreloadRunning,
    imagePreloadStatus,
    ensureActivityImages,
    loadTrips,
    loadTrip,
    loadCatalogAttractions,
    updateAvailability,
    removeActivity,
    regenerateActivity,
    addCatalogAttraction,
    deleteTrip,
    preloadUpcomingImages,
    logout
  }
}
