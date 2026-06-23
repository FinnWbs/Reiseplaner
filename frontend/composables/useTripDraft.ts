export type TripDraft = {
  city: string
  country?: string
  countryCode?: string
  state?: string
  latitude?: number
  longitude?: number
  placeId?: string
  destinationSource?: 'KNOWN' | 'SUGGESTED'
  datesKnown: boolean
  startDate: string
  endDate: string
  daysCount: number
  planningDates: string[]
  interestNames: string[]
  pace: 'RELAXED' | 'BALANCED' | 'ACTIVE'
  dayRhythm: 'EARLY' | 'BALANCED' | 'LATE'
}

const tripDraftKey = 'travelmate-trip-draft'

export const useTripDraft = () => {
  const draft = useState<TripDraft | null>('trip-draft', () => null)

  const loadDraft = () => {
    if (!import.meta.client || draft.value) return draft.value
    const raw = sessionStorage.getItem(tripDraftKey)
    if (!raw) return null
    try {
      draft.value = JSON.parse(raw) as TripDraft
    } catch {
      sessionStorage.removeItem(tripDraftKey)
    }
    return draft.value
  }

  const saveDraft = (value: TripDraft) => {
    draft.value = value
    if (import.meta.client) {
      sessionStorage.setItem(tripDraftKey, JSON.stringify(value))
    }
  }

  const clearDraft = () => {
    draft.value = null
    if (import.meta.client) sessionStorage.removeItem(tripDraftKey)
  }

  const hasDraft = () => Boolean(loadDraft())

  return { draft, loadDraft, saveDraft, clearDraft, hasDraft }
}
