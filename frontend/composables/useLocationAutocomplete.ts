import type { Ref } from 'vue'
import type { LocationSuggestion } from '~/types/trip'

const locationErrorMessage = (err: any, fallback: string) =>
  err?.data?.message || err?.data?.error || err?.response?._data?.message || err?.message || fallback

export const useLocationAutocomplete = (city: Ref<string>) => {
  const { request } = useApi()
  const locationSuggestions = ref<LocationSuggestion[]>([])
  const selectedLocation = ref<LocationSuggestion | null>(null)
  const locationLoading = ref(false)
  const locationError = ref('')
  const locationDropdownOpen = ref(false)
  const highlightedLocationIndex = ref(-1)
  let locationSearchTimer: ReturnType<typeof setTimeout> | null = null

  const describeLocation = (suggestion: LocationSuggestion) =>
    [suggestion.state, suggestion.country].filter(Boolean).join(', ')

  const fetchLocationSuggestions = async () => {
    const query = city.value.trim()
    selectedLocation.value = selectedLocation.value?.city === query ? selectedLocation.value : null
    locationError.value = ''
    if (query.length < 2) {
      clearLocationSuggestions()
      return
    }
    locationLoading.value = true
    try {
      locationSuggestions.value = await request<LocationSuggestion[]>(
        `/locations/autocomplete?query=${encodeURIComponent(query)}`
      )
      locationDropdownOpen.value = locationSuggestions.value.length > 0
      highlightedLocationIndex.value = locationSuggestions.value.length > 0 ? 0 : -1
    } catch (err: any) {
      clearLocationSuggestions()
      locationError.value = locationErrorMessage(err, 'Standortvorschlaege konnten nicht geladen werden.')
    } finally {
      locationLoading.value = false
    }
  }

  const scheduleLocationSearch = () => {
    selectedLocation.value = selectedLocation.value?.city === city.value.trim() ? selectedLocation.value : null
    if (locationSearchTimer) clearTimeout(locationSearchTimer)
    locationSearchTimer = setTimeout(fetchLocationSuggestions, 300)
  }

  const selectLocation = (suggestion: LocationSuggestion) => {
    selectedLocation.value = suggestion
    city.value = suggestion.city
    clearLocationSuggestions()
    locationError.value = ''
  }

  const handleLocationKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      locationDropdownOpen.value = false
      highlightedLocationIndex.value = -1
      return
    }
    if (!locationDropdownOpen.value || locationSuggestions.value.length === 0) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      highlightedLocationIndex.value =
        (highlightedLocationIndex.value + 1) % locationSuggestions.value.length
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      highlightedLocationIndex.value =
        (highlightedLocationIndex.value - 1 + locationSuggestions.value.length) % locationSuggestions.value.length
    } else if (event.key === 'Enter' && highlightedLocationIndex.value >= 0) {
      event.preventDefault()
      selectLocation(locationSuggestions.value[highlightedLocationIndex.value])
    }
  }

  const clearLocationSuggestions = () => {
    locationSuggestions.value = []
    locationDropdownOpen.value = false
    highlightedLocationIndex.value = -1
  }

  const resetLocationAutocomplete = () => {
    selectedLocation.value = null
    locationError.value = ''
    clearLocationSuggestions()
  }

  const cleanupLocationAutocomplete = () => {
    if (locationSearchTimer) clearTimeout(locationSearchTimer)
  }

  return {
    locationSuggestions,
    selectedLocation,
    locationLoading,
    locationError,
    locationDropdownOpen,
    highlightedLocationIndex,
    describeLocation,
    scheduleLocationSearch,
    selectLocation,
    handleLocationKeydown,
    clearLocationSuggestions,
    resetLocationAutocomplete,
    cleanupLocationAutocomplete
  }
}
