<script setup lang="ts">
import { ExternalLink, MapPinned } from 'lucide-vue-next'
import type { Map as LeafletMap, Marker } from 'leaflet'
import type { TripActivity } from '~/types/trip'
import { googleMapsCityUrl, googleMapsUrl } from '~/utils/maps'

const props = defineProps<{
  activities: TripActivity[]
  city: string
  selectedActivityId: number | null
}>()

const emit = defineEmits<{
  select: [activityId: number]
}>()

const config = useRuntimeConfig()
const mapElement = ref<HTMLElement | null>(null)
let map: LeafletMap | null = null
let leaflet: typeof import('leaflet') | null = null
const markers = new Map<number, Marker>()

const mappedActivities = computed(() => props.activities.filter(item =>
  item.activity.latitude != null && item.activity.longitude != null
))

const markerIcon = (position: number, selected: boolean) => leaflet!.divIcon({
  className: 'activity-map-marker-shell',
  html: `<span class="activity-map-marker${selected ? ' selected' : ''}">${position}</span>`,
  iconSize: [34, 34],
  iconAnchor: [17, 17],
  popupAnchor: [0, -18]
})

const popupContent = (item: TripActivity) => {
  const wrapper = document.createElement('div')
  wrapper.className = 'activity-map-popup'

  const meta = document.createElement('span')
  meta.textContent = `${formatMinutes(item.scheduledStart)} · Stopp ${item.position}`
  wrapper.append(meta)

  const title = document.createElement('strong')
  title.textContent = item.activity.name
  wrapper.append(title)

  const link = document.createElement('a')
  link.href = googleMapsUrl(item, props.city)
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  link.textContent = 'In Google Maps öffnen'
  wrapper.append(link)
  return wrapper
}

const renderMarkers = () => {
  if (!map || !leaflet) return
  markers.forEach(marker => marker.remove())
  markers.clear()

  const points: [number, number][] = []
  mappedActivities.value.forEach((item) => {
    const point: [number, number] = [item.activity.latitude!, item.activity.longitude!]
    points.push(point)
    const marker = leaflet!.marker(point, {
      icon: markerIcon(item.position, item.id === props.selectedActivityId),
      keyboard: true,
      title: item.activity.name
    })
      .addTo(map!)
      .bindPopup(popupContent(item))
      .on('click', () => emit('select', item.id))
    markers.set(item.id, marker)
  })

  if (points.length === 1) map.setView(points[0], 14)
  if (points.length > 1) map.fitBounds(points, { padding: [42, 42], maxZoom: 15 })
}

const initializeMap = async () => {
  if (!mapElement.value || map) return
  leaflet = await import('leaflet')
  map = leaflet.map(mapElement.value, {
    zoomControl: true,
    scrollWheelZoom: false
  })
  leaflet.tileLayer(config.public.mapTileUrl, {
    attribution: config.public.mapAttribution,
    maxZoom: 19
  }).addTo(map)
  renderMarkers()
  requestAnimationFrame(() => map?.invalidateSize())
}

watch(mappedActivities, async (activities) => {
  if (activities.length && !map) {
    await nextTick()
    await initializeMap()
    return
  }
  renderMarkers()
}, { deep: true })
watch(() => props.selectedActivityId, (selectedId) => {
  if (!leaflet) return
  markers.forEach((marker, id) => {
    const item = props.activities.find(activity => activity.id === id)
    if (item) marker.setIcon(markerIcon(item.position, id === selectedId))
  })
  if (selectedId != null) markers.get(selectedId)?.openPopup()
})

onMounted(initializeMap)
onBeforeUnmount(() => {
  map?.remove()
  map = null
  markers.clear()
})
</script>

<template>
  <section class="activity-day-map" aria-label="Aktivitäten auf der Karte">
    <header>
      <div>
        <span class="orbit-kicker">Tageskarte</span>
        <h3>Orte auf einen Blick</h3>
      </div>
      <a :href="googleMapsCityUrl(city)" target="_blank" rel="noopener noreferrer">
        Google Maps <ExternalLink :size="15" />
      </a>
    </header>
    <div v-if="mappedActivities.length" ref="mapElement" class="activity-map-canvas" />
    <div v-else class="activity-map-empty">
      <MapPinned :size="30" />
      <strong>Noch keine Kartenpositionen</strong>
      <p>Für diese Aktivitäten sind noch keine Koordinaten gespeichert.</p>
      <a :href="googleMapsCityUrl(city)" target="_blank" rel="noopener noreferrer">
        {{ city }} in Google Maps ansehen <ExternalLink :size="15" />
      </a>
    </div>
  </section>
</template>
