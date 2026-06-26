export type Interest = { id: number; name: string }

export type ActivityImage = {
  url: string
  alt: string
  credit?: string
}

export type TripActivity = {
  id: number
  position: number
  locked: boolean
  notes?: string
  scheduledStart: number
  durationMinutes: number
  fitsAvailability: boolean
  activity: {
    id: number
    name: string
    description?: string
    category?: string
    subcategory?: string
    address?: string
    rating?: number
    dataQualityScore: number
    latitude?: number
    longitude?: number
    images?: ActivityImage[]
  }
}

export type TripDay = {
  id: number
  dayNumber: number
  travelDate?: string
  weekday?: string
  availableFrom: number
  availableUntil: number
  activities: TripActivity[]
}

export type Trip = {
  id: number
  city: string
  country?: string
  countryCode?: string
  state?: string
  latitude?: number
  longitude?: number
  placeId?: string
  daysCount: number
  status: string
  startDate?: string
  endDate?: string
  pace: 'RELAXED' | 'BALANCED' | 'ACTIVE'
  dayRhythm: 'EARLY' | 'BALANCED' | 'LATE'
  destinationSource: 'KNOWN' | 'SUGGESTED'
  days: TripDay[]
}

export type LocationSuggestion = {
  id: string
  city: string
  country?: string
  countryCode?: string
  state?: string
  formatted?: string
  latitude?: number
  longitude?: number
  placeId?: string
}
