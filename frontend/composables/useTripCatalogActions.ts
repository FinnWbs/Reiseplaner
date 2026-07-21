import type { Ref } from 'vue'
import type { CatalogAttractionResponse, Trip } from '~/types/trip'
import { workspaceErrorMessage } from '~/utils/workspaceErrors'

type ApiRequest = <T>(path: string, options?: any) => Promise<T>

export const useTripCatalogActions = (
  trip: Ref<Trip | null>,
  request: ApiRequest,
  replaceTrip: (updated: Trip) => void,
  workspaceError: Ref<string>
) => {
  const catalog = ref<CatalogAttractionResponse | null>(null)
  const catalogLoading = ref(false)
  const catalogError = ref('')
  const addingCatalogId = ref<string | null>(null)
  let catalogRequest: Promise<void> | null = null
  let catalogRequestTripId: number | null = null

  const loadCatalogAttractions = async () => {
    if (!trip.value) return
    const tripId = trip.value.id

    if (catalogRequest && catalogRequestTripId === tripId) {
      await catalogRequest
      return
    }

    if (catalogRequestTripId !== tripId) {
      catalog.value = null
      catalogError.value = ''
    }

    catalogRequestTripId = tripId
    catalogLoading.value = true
    catalogError.value = ''
    catalogRequest = (async () => {
      try {
        const loadedCatalog = await request<CatalogAttractionResponse>(`/trips/${tripId}/catalog-attractions`)
        if (trip.value?.id === tripId) {
          catalog.value = loadedCatalog
        }
      } catch (err: any) {
        if (trip.value?.id === tripId) {
          catalogError.value = workspaceErrorMessage(err, 'Highlights konnten nicht geladen werden.')
        }
      } finally {
        if (catalogRequestTripId === tripId) {
          catalogLoading.value = false
          catalogRequest = null
        }
      }
    })()

    await catalogRequest
  }

  const addCatalogAttraction = async (dayId: number, catalogId: string) => {
    if (!trip.value) return
    addingCatalogId.value = catalogId
    catalogError.value = ''
    workspaceError.value = ''
    try {
      replaceTrip(await request<Trip>(
        `/trips/${trip.value.id}/days/${dayId}/catalog-attractions/${encodeURIComponent(catalogId)}`,
        { method: 'POST', body: {} }
      ))
      await loadCatalogAttractions()
    } catch (err: any) {
      const message = workspaceErrorMessage(err, 'Highlight konnte nicht hinzugefuegt werden.')
      catalogError.value = message
      workspaceError.value = message
    } finally {
      addingCatalogId.value = null
    }
  }

  return {
    catalog,
    catalogLoading,
    catalogError,
    addingCatalogId,
    loadCatalogAttractions,
    addCatalogAttraction
  }
}
