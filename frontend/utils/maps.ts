import type { TripActivity } from '~/types/trip'

export const googleMapsUrl = (item: TripActivity, city?: string) => {
  const activity = item.activity
  const query = activity.latitude != null && activity.longitude != null
    ? `${activity.latitude},${activity.longitude}`
    : [activity.name, activity.address, city].filter(Boolean).join(', ')

  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`
}

export const googleMapsCityUrl = (city: string) =>
  `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(city)}`
