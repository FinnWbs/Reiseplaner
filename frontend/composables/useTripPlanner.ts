import type { Interest, Trip, TripDay } from '~/types/trip'

const warmCities = ['Barcelona', 'Rom', 'Lissabon', 'Athen']
const coolCities = ['Berlin', 'Amsterdam', 'Prag', 'Kopenhagen', 'Stockholm']

const datesBetween = (from: string, until: string) => {
  if (!from || !until || until < from) return []
  const dates: string[] = []
  const cursor = new Date(`${from}T12:00:00`)
  const end = new Date(`${until}T12:00:00`)
  while (cursor <= end && dates.length < 31) {
    dates.push(cursor.toISOString().slice(0, 10))
    cursor.setDate(cursor.getDate() + 1)
  }
  return dates
}

const plannerErrorMessage = (err: any, fallback: string) =>
  err?.data?.message || err?.data?.error || err?.response?._data?.message || err?.message || fallback

const isNotFoundError = (err: any) =>
  err?.statusCode === 404 || err?.response?.status === 404 || err?.status === 404

const normalizeInterestLabel = (value: string) =>
  value
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .trim()
    .toLowerCase()

const draftInterestAliases: Record<string, string> = {
  kultur: 'CULTURE',
  geschichte: 'HISTORY',
  natur: 'NATURE',
  food: 'FOOD',
  essen: 'FOOD',
  shopping: 'SHOPPING',
  nightlife: 'NIGHTLIFE',
  nachtleben: 'NIGHTLIFE',
  sport: 'ADVENTURE',
  abenteuer: 'ADVENTURE'
}

const matchesDraftInterest = (interest: Interest, draftName: string) => {
  const normalizedDraftName = normalizeInterestLabel(draftName)
  const aliasCode = draftInterestAliases[normalizedDraftName]
  return interest.key === aliasCode || normalizeInterestLabel(interest.name) === normalizedDraftName
}

export const useTripPlanner = () => {
  const { request } = useApi()
  const { clearAuth, hydrateAuth, token, user } = useAuth()
  const tripDraft = useTripDraft()

  const interests = ref<Interest[]>([])
  const trips = ref<Trip[]>([])
  const activeTrip = ref<Trip | null>(null)
  const error = ref('')
  const tripError = ref('')
  const loading = ref(false)
  const savingSchedule = ref(false)
  const deletingActivityId = ref<number | null>(null)
  const regeneratingActivityId = ref<number | null>(null)
  const editingDates = ref(false)
  const savingDates = ref(false)
  const editStartDate = ref('')
  const editEndDate = ref('')
  const editPlanningDates = ref<string[]>([])

  const interviewStep = ref(1)
  const destinationKnown = ref<boolean | null>(null)
  const climate = ref<'WARM' | 'COOL'>('WARM')
  const city = ref('')
  const datesKnown = ref<boolean | null>(null)
  const startDate = ref('')
  const endDate = ref('')
  const daysCount = ref(3)
  const planningDates = ref<string[]>([])
  const selectedInterestIds = ref<number[]>([])
  const pace = ref<'RELAXED' | 'BALANCED' | 'ACTIVE'>('BALANCED')
  const dayRhythm = ref<'EARLY' | 'BALANCED' | 'LATE'>('BALANCED')
  const location = useLocationAutocomplete(city)

  const isLoggedIn = computed(() => Boolean(token.value))
  const citySuggestions = computed(() => climate.value === 'WARM' ? warmCities : coolCities)
  const dateOptions = computed(() => datesBetween(startDate.value, endDate.value))
  const editDateOptions = computed(() => datesBetween(editStartDate.value, editEndDate.value))
  const stepReady = computed(() => {
    if (interviewStep.value === 1) return destinationKnown.value !== null
    if (interviewStep.value === 2) return Boolean(city.value.trim())
    if (interviewStep.value === 3) return datesKnown.value !== null
    if (interviewStep.value === 4) {
      return datesKnown.value
        ? Boolean(startDate.value && endDate.value && endDate.value >= startDate.value)
        : daysCount.value >= 1 && daysCount.value <= 14
    }
    if (interviewStep.value === 5) {
      return !datesKnown.value || planningDates.value.length > 0
    }
    if (interviewStep.value === 6) return selectedInterestIds.value.length > 0
    return true
  })

  watch([startDate, endDate], () => {
    planningDates.value = planningDates.value.filter(date => dateOptions.value.includes(date))
    if (datesKnown.value && dateOptions.value.length > 0 && planningDates.value.length === 0) {
      planningDates.value = [...dateOptions.value]
    }
  })

  watch([editStartDate, editEndDate], () => {
    editPlanningDates.value = editPlanningDates.value.filter(date => editDateOptions.value.includes(date))
  })

  const loadInitialData = async () => {
    interests.value = await request<Interest[]>('/interests')
    trips.value = await request<Trip[]>('/trips')
    activeTrip.value = trips.value[0] ?? null
  }

  const selectSuggestedCity = (suggestion: string) => {
    city.value = suggestion
    location.resetLocationAutocomplete()
  }

  const toggleInterest = (id: number) => {
    selectedInterestIds.value = selectedInterestIds.value.includes(id)
      ? selectedInterestIds.value.filter(item => item !== id)
      : [...selectedInterestIds.value, id]
  }

  const togglePlanningDate = (date: string) => {
    planningDates.value = planningDates.value.includes(date)
      ? planningDates.value.filter(item => item !== date)
      : [...planningDates.value, date].sort()
  }

  const beginDateEdit = () => {
    if (!activeTrip.value) return
    editStartDate.value = activeTrip.value.startDate || ''
    editEndDate.value = activeTrip.value.endDate || ''
    editPlanningDates.value = activeTrip.value.days
      .map(day => day.travelDate)
      .filter((date): date is string => Boolean(date))
    editingDates.value = true
  }

  const toggleEditPlanningDate = (date: string) => {
    editPlanningDates.value = editPlanningDates.value.includes(date)
      ? editPlanningDates.value.filter(item => item !== date)
      : [...editPlanningDates.value, date].sort()
  }

  const saveDates = async () => {
    if (!activeTrip.value || !editStartDate.value || !editEndDate.value || editPlanningDates.value.length === 0) return
    savingDates.value = true
    tripError.value = ''
    try {
      replaceTripState(await request<Trip>(`/trips/${activeTrip.value.id}/dates`, {
        method: 'PUT',
        body: {
          startDate: editStartDate.value,
          endDate: editEndDate.value,
          planningDates: editPlanningDates.value
        }
      }))
      editingDates.value = false
    } catch (err: any) {
      tripError.value = plannerErrorMessage(err, 'Reisezeitraum konnte nicht gespeichert werden.')
    } finally {
      savingDates.value = false
    }
  }

  const nextStep = () => {
    if (!stepReady.value || interviewStep.value >= 7) return
    interviewStep.value++
  }

  const previousStep = () => {
    if (interviewStep.value > 1) interviewStep.value--
  }

  const resetInterview = () => {
    interviewStep.value = 1
    destinationKnown.value = null
    climate.value = 'WARM'
    city.value = ''
    datesKnown.value = null
    startDate.value = ''
    endDate.value = ''
    daysCount.value = 3
    planningDates.value = []
    selectedInterestIds.value = []
    location.resetLocationAutocomplete()
    pace.value = 'BALANCED'
    dayRhythm.value = 'BALANCED'
  }

  const createTrip = async () => {
    error.value = ''
    loading.value = true
    try {
      const selectedDates = datesKnown.value ? planningDates.value : []
      const created = await request<Trip>('/trips', {
        method: 'POST',
        body: {
          city: city.value,
          daysCount: selectedDates.length || daysCount.value,
          interestIds: selectedInterestIds.value,
          startDate: datesKnown.value ? startDate.value : null,
          endDate: datesKnown.value ? endDate.value : null,
          planningDates: selectedDates,
          pace: pace.value,
          dayRhythm: dayRhythm.value,
          destinationSource: destinationKnown.value ? 'KNOWN' : 'SUGGESTED',
          country: location.selectedLocation.value?.country,
          countryCode: location.selectedLocation.value?.countryCode,
          state: location.selectedLocation.value?.state,
          latitude: location.selectedLocation.value?.latitude,
          longitude: location.selectedLocation.value?.longitude,
          placeId: location.selectedLocation.value?.placeId
        }
      })
      activeTrip.value = created
      trips.value = [created, ...trips.value.filter(trip => trip.id !== created.id)]
      resetInterview()
      return created
    } catch (err: any) {
      error.value = plannerErrorMessage(err, 'Reise konnte nicht erstellt werden.')
      return null
    } finally {
      loading.value = false
    }
  }

  const createTripFromDraft = async () => {
    const draft = tripDraft.loadDraft()
    if (!draft) return null

    city.value = draft.city
    location.selectedLocation.value = draft.city && (
      draft.placeId || draft.latitude != null || draft.country
    ) ? {
      id: draft.placeId || `${draft.city}-${draft.countryCode || ''}`,
      city: draft.city,
      country: draft.country,
      countryCode: draft.countryCode,
      state: draft.state,
      latitude: draft.latitude,
      longitude: draft.longitude,
      placeId: draft.placeId
    } : null
    datesKnown.value = draft.datesKnown
    startDate.value = draft.startDate
    endDate.value = draft.endDate
    daysCount.value = draft.daysCount
    planningDates.value = [...draft.planningDates]
    pace.value = draft.pace
    dayRhythm.value = draft.dayRhythm
    selectedInterestIds.value = interests.value
      .filter(interest => draft.interestNames.some(name => matchesDraftInterest(interest, name)))
      .map(interest => interest.id)
    destinationKnown.value = draft.destinationSource !== 'SUGGESTED'

    const created = await createTrip()
    if (created) tripDraft.clearDraft()
    return created
  }

  const replaceTripState = (updated: Trip) => {
    activeTrip.value = updated
    trips.value = trips.value.map(trip => trip.id === updated.id ? updated : trip)
  }

  const refreshActiveTrip = async () => {
    if (!activeTrip.value) return
    try {
      replaceTripState(await request<Trip>(`/trips/${activeTrip.value.id}`))
    } catch (err: any) {
      if (!isNotFoundError(err)) throw err
      trips.value = await request<Trip[]>('/trips')
      activeTrip.value = trips.value[0] ?? null
    }
  }

  const removeActivity = async (dayId: number, itemId: number) => {
    if (!activeTrip.value) return
    tripError.value = ''
    deletingActivityId.value = itemId
    try {
      replaceTripState(await request<Trip>(
        `/trips/${activeTrip.value.id}/days/${dayId}/activities/${itemId}`,
        { method: 'DELETE' }
      ))
    } catch (err: any) {
      if (isNotFoundError(err)) {
        await refreshActiveTrip()
        tripError.value = 'Der Reiseplan wurde aktualisiert. Bitte versuche es mit dem neu geladenen Plan erneut.'
      } else {
        tripError.value = plannerErrorMessage(err, 'Aktivität konnte nicht entfernt werden.')
      }
    } finally {
      deletingActivityId.value = null
    }
  }

  const regenerateActivity = async (dayId: number, itemId: number) => {
    if (!activeTrip.value) return
    tripError.value = ''
    regeneratingActivityId.value = itemId
    try {
      replaceTripState(await request<Trip>(
        `/trips/${activeTrip.value.id}/days/${dayId}/activities/${itemId}/regenerate`,
        { method: 'POST' }
      ))
    } catch (err: any) {
      if (isNotFoundError(err)) {
        await refreshActiveTrip()
        tripError.value = 'Der Reiseplan wurde aktualisiert. Bitte versuche es mit dem neu geladenen Plan erneut.'
      } else {
        tripError.value = plannerErrorMessage(err, 'Keine passende Alternative gefunden.')
      }
    } finally {
      regeneratingActivityId.value = null
    }
  }

  const persistSchedule = async () => {
    if (!activeTrip.value || savingSchedule.value) return
    savingSchedule.value = true
    tripError.value = ''
    try {
      replaceTripState(await request<Trip>(`/trips/${activeTrip.value.id}/schedule`, {
        method: 'PUT',
        body: {
          days: activeTrip.value.days.map(day => ({
            dayId: day.id,
            activityItemIds: day.activities.map(item => item.id)
          }))
        }
      }))
    } catch (err: any) {
      tripError.value = plannerErrorMessage(err, 'Der Zeitplan konnte nicht gespeichert werden.')
      activeTrip.value = await request<Trip>(`/trips/${activeTrip.value.id}`)
    } finally {
      savingSchedule.value = false
    }
  }

  const updateAvailability = async (day: TripDay) => {
    if (!activeTrip.value) return
    if (day.availableFrom >= day.availableUntil) {
      day.availableUntil = Math.min(1440, day.availableFrom + 30)
    }
    tripError.value = ''
    try {
      replaceTripState(await request<Trip>(
        `/trips/${activeTrip.value.id}/days/${day.id}/availability`,
        {
          method: 'PUT',
          body: {
            availableFrom: day.availableFrom,
            availableUntil: day.availableUntil
          }
        }
      ))
    } catch (err: any) {
      if (isNotFoundError(err)) {
        await refreshActiveTrip()
        tripError.value = 'Der Reisetag war nicht mehr aktuell. Ich habe den Plan neu geladen.'
      } else {
        tripError.value = plannerErrorMessage(err, 'Zeitfenster konnte nicht gespeichert werden.')
      }
    }
  }

  const deleteTrip = async (tripId: number) => {
    error.value = ''
    try {
      await request<void>(`/trips/${tripId}`, { method: 'DELETE' })
      trips.value = trips.value.filter(trip => trip.id !== tripId)
      if (activeTrip.value?.id === tripId) activeTrip.value = trips.value[0] ?? null
    } catch (err: any) {
      error.value = plannerErrorMessage(err, 'Reise konnte nicht gelöscht werden.')
    }
  }

  const logout = async () => {
    clearAuth()
    await navigateTo('/auth')
  }

  const initialize = async () => {
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
  }

  return {
    interests,
    trips,
    activeTrip,
    error,
    tripError,
    loading,
    deletingActivityId,
    regeneratingActivityId,
    editingDates,
    savingDates,
    editStartDate,
    editEndDate,
    editPlanningDates,
    interviewStep,
    destinationKnown,
    climate,
    city,
    datesKnown,
    startDate,
    endDate,
    daysCount,
    planningDates,
    selectedInterestIds,
    pace,
    dayRhythm,
    isLoggedIn,
    citySuggestions,
    dateOptions,
    editDateOptions,
    stepReady,
    user,
    ...location,
    initialize,
    logout,
    selectSuggestedCity,
    toggleInterest,
    togglePlanningDate,
    beginDateEdit,
    toggleEditPlanningDate,
    saveDates,
    nextStep,
    previousStep,
    createTrip,
    createTripFromDraft,
    removeActivity,
    regenerateActivity,
    persistSchedule,
    updateAvailability,
    deleteTrip,
    formatMinutes,
    formatDate,
    weekdayFor
  }
}

export const formatMinutes = (minutes: number) => {
  if (minutes === 1440) return '24:00'
  const hours = Math.floor(minutes / 60).toString().padStart(2, '0')
  const mins = (minutes % 60).toString().padStart(2, '0')
  return `${hours}:${mins}`
}

export const formatDate = (date?: string) => {
  if (!date) return ''
  return new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
    .format(new Date(`${date}T12:00:00`))
}

export const weekdayFor = (date: string) => new Intl.DateTimeFormat('de-DE', { weekday: 'short' })
  .format(new Date(`${date}T12:00:00`))
