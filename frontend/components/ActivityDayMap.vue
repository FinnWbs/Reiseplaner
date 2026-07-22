<script lang="ts">
const rememberedActivityMapViews = new Map<string, { center: [number, number], zoom: number }>()
</script>

<script setup lang="ts">
import { ExternalLink, LocateFixed, MapPinned } from 'lucide-vue-next'
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
const userMovedMap = ref(false)
let map: LeafletMap | null = null
let leaflet: typeof import('leaflet') | null = null
let suppressMoveTracking = false
const markers = new Map<number, Marker>()

const mappedActivities = computed(() => props.activities.filter(item =>
  item.activity.latitude != null && item.activity.longitude != null
))

const mappedSignature = computed(() => mappedActivities.value.map(item =>
  `${item.id}:${item.position}:${item.activity.latitude}:${item.activity.longitude}`
).join('|'))

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
      .bindPopup(popupContent(item), { autoPan: false })
      .on('click', () => emit('select', item.id))
    markers.set(item.id, marker)
  })
}

const fitToActivities = () => {
  if (!map) return
  const points = mappedActivities.value.map(item =>
    [item.activity.latitude!, item.activity.longitude!] as [number, number]
  )
  if (!points.length) return

  suppressMoveTracking = true
  if (points.length === 1) map.setView(points[0], 14)
  if (points.length > 1) map.fitBounds(points, { padding: [42, 42], maxZoom: 15 })

  userMovedMap.value = false
  rememberedActivityMapViews.delete(props.city)
  window.setTimeout(() => {
    suppressMoveTracking = false
  }, 80)
}

const markUserMapMove = () => {
  if (!suppressMoveTracking) userMovedMap.value = true
}

const rememberUserMapView = () => {
  if (!map || suppressMoveTracking || !userMovedMap.value) return
  const center = map.getCenter()
  rememberedActivityMapViews.set(props.city, {
    center: [center.lat, center.lng],
    zoom: map.getZoom()
  })
}

const restoreRememberedMapView = () => {
  if (!map) return false
  const remembered = rememberedActivityMapViews.get(props.city)
  if (!remembered) return false

  suppressMoveTracking = true
  map.setView(remembered.center, remembered.zoom, { animate: false })
  userMovedMap.value = true
  window.setTimeout(() => {
    suppressMoveTracking = false
  }, 80)
  return true
}

const initializeMap = async () => {
  if (!mapElement.value || map) return
  leaflet = await import('leaflet')
  map = leaflet.map(mapElement.value, {
    zoomControl: true,
    scrollWheelZoom: true
  })
  map.on('dragstart zoomstart', markUserMapMove)
  map.on('moveend zoomend', rememberUserMapView)
  leaflet.tileLayer(config.public.mapTileUrl, {
    attribution: config.public.mapAttribution,
    maxZoom: 19
  }).addTo(map)
  renderMarkers()
  if (!restoreRememberedMapView()) fitToActivities()
  requestAnimationFrame(() => map?.invalidateSize())
}

watch(mappedSignature, async () => {
  if (mappedActivities.value.length && !map) {
    await nextTick()
    await initializeMap()
    return
  }
  renderMarkers()
  if (!userMovedMap.value) fitToActivities()
  else requestAnimationFrame(() => map?.invalidateSize())
})

watch(() => props.selectedActivityId, (selectedId) => {
  if (!leaflet) return
  markers.forEach((marker, id) => {
    const item = props.activities.find(activity => activity.id === id)
    if (item) marker.setIcon(markerIcon(item.position, id === selectedId))
  })
  if (selectedId != null && !userMovedMap.value) markers.get(selectedId)?.openPopup()
})

onMounted(initializeMap)
onBeforeUnmount(() => {
  map?.off('dragstart zoomstart', markUserMapMove)
  map?.off('moveend zoomend', rememberUserMapView)
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
    <div v-if="mappedActivities.length" class="activity-map-frame">
      <div ref="mapElement" class="activity-map-canvas" />
      <button
        class="activity-map-recenter"
        type="button"
        aria-label="Karte wieder auf Aktivitäten zentrieren"
        @click="fitToActivities"
      >
        <LocateFixed :size="16" />
        <span>Stopps zentrieren</span>
      </button>
    </div>
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
