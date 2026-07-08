import type { TripActivity } from '~/types/trip'

export const googleMapsUrl = (item: TripActivity, city?: string) => {
  const activity = item.activity
  const addressContainsCity = Boolean(city && activity.address?.toLowerCase().includes(city.toLowerCase()))
  const addressQuery = [
    activity.name,
    activity.address,
    addressContainsCity ? '' : city
  ].filter(Boolean).join(', ')
  const coordinateQuery = activity.latitude != null && activity.longitude != null
    ? `${activity.latitude},${activity.longitude}`
    : ''
  const query = addressQuery || coordinateQuery

  if (addressQuery && coordinateQuery) {
    return `https://www.google.com/maps/search/${encodeURIComponent(addressQuery)}/@${coordinateQuery},18z`
  }

  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`
}

export const googleMapsCityUrl = (city: string) =>
  `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(city)}`
