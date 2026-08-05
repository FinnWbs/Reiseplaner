export type Interest = { id: number; key: string; name: string }

export type ActivityImage = {
  url: string
  alt: string
  credit?: string
  source?: string
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
    primaryInterest?: string
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
  preferredMonth?: string
  pace: 'RELAXED' | 'BALANCED' | 'ACTIVE'
  dayRhythm: 'EARLY' | 'BALANCED' | 'LATE'
  destinationSource: 'KNOWN' | 'SUGGESTED'
  selectedInterests?: string[]
  days: TripDay[]
}

export type CatalogAttraction = {
  catalogId: string
  name: string
  city: string
  wikidataId?: string
  wikipediaProject?: string
  wikipediaTitle?: string
  primaryInterest: string
  category: string
  latitude?: number
  longitude?: number
  rank: number
  description?: string
  publicAttractionScore?: number
  pageviews?: number
  sitelinkCount?: number
  source?: string
  alreadyPlanned: boolean
  plannedDayNumbers: number[]
}

export type CatalogAttractionResponse = {
  supported: boolean
  message: string
  items: CatalogAttraction[]
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
