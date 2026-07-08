<script setup lang="ts">
import {
  ArrowLeft,
  ArrowRight,
  Camera,
  MapPin,
  Maximize
} from 'lucide-vue-next'
import type { ActivityImage, TripActivity } from '~/types/trip'

type GalleryCategory = 'Kultur' | 'Geschichte' | 'Natur' | 'Food' | 'Shopping' | 'Nightlife' | 'Sport'
type GalleryImage = {
  url: string
  alt: string
  credit?: string
  source?: string
}
type ImageLoadStatus = 'idle' | 'loading' | 'ready' | 'failed'
type GallerySlide = {
  key: string
  image: GalleryImage
  realImage: GalleryImage | null
  fallbackImage: GalleryImage
  label: string
  variant: number
}

const props = defineProps<{
  activity: TripActivity
  city: string
}>()

const emit = defineEmits<{
  requestImages: [activityId: number]
}>()

const config = useRuntimeConfig()
const activeSlide = ref(0)
const failedImageUrls = ref<string[]>([])
const loadedImageUrls = ref<string[]>([])
const imageRetryVersions = ref<Record<string, number>>({})
const imageLoadStatus = ref<ImageLoadStatus>('idle')
const expanded = ref(false)
const imageRetryAfterMs = 7000
const failedImageRetryTimers = new Map<string, ReturnType<typeof setTimeout>>()

const debugGallery = (event: string, extra: Record<string, unknown> = {}) => {
  if (!import.meta.dev) return
  console.debug('[ActivityGallery]', event, {
    tripActivityId: props.activity?.id,
    activityId: props.activity?.activity?.id,
    propImages: propImages.value.length,
    localImages: localImages.value.length,
    realImages: realImages.value.length,
    slides: displaySlides.value.length,
    placeholderOnly: displaySlides.value.length === 1 && !displaySlides.value[0]?.realImage,
    status: imageLoadStatus.value,
    ...extra
  })
}

const normalizeText = (value: unknown) => String(value || '')
  .normalize('NFD')
  .replace(/\p{M}/gu, '')
  .toLowerCase()

const categoryName = computed<GalleryCategory>(() => {
  const value = normalizeText(`${props.activity?.activity?.category || ''} ${props.activity?.activity?.subcategory || ''}`)
  if (/night|club|bar|pub|nacht/.test(value)) return 'Nightlife'
  if (/food|essen|restaurant|cafe|cafes|market|markt|catering/.test(value)) return 'Food'
  if (/park|natur|garden|garten|forest|wald|beach|strand/.test(value)) return 'Natur'
  if (/shop|shopping|commercial|mall|markt|markte/.test(value)) return 'Shopping'
  if (/sport|stadium|fitness/.test(value)) return 'Sport'
  if (/heritage|historic|historisch|monument|castle|schloss|geschichte/.test(value)) return 'Geschichte'
  return 'Kultur'
})

const categoryCopy: Record<GalleryCategory, string[]> = {
  Kultur: ['Kunst & Atmosphäre', 'Details entdecken', 'Ein Ort mit Charakter'],
  Geschichte: ['Spuren der Vergangenheit', 'Architektur im Detail', 'Geschichten vor Ort'],
  Natur: ['Naturerlebnis', 'Neue Perspektiven', 'Zeit zum Durchatmen'],
  Food: ['Geschmack der Stadt', 'Frisch serviert', 'Genuss im Detail'],
  Shopping: ['Lokale Entdeckungen', 'Design & Auswahl', 'Besondere Fundstücke'],
  Nightlife: ['Abendstimmung', 'Lichter der Stadt', 'Nachtleben entdecken'],
  Sport: ['Bewegung erleben', 'Dynamische Momente', 'Aktiv unterwegs']
}

const categoryFallbackImages: Record<GalleryCategory, { url: string; alt: string }> = {
  Kultur: {
    url: '/images/activity-fallbacks/culture-01.png',
    alt: 'Kultureller Ort mit Galerie-Atmosphäre'
  },
  Geschichte: {
    url: '/images/activity-fallbacks/history-01.png',
    alt: 'Historische Architektur als Reiseeindruck'
  },
  Natur: {
    url: '/images/activity-fallbacks/nature-01.png',
    alt: 'Ruhiger Park und Naturerlebnis in der Stadt'
  },
  Food: {
    url: '/images/activity-fallbacks/food-01.png',
    alt: 'Lokales Essen in gemütlicher Restaurantatmosphäre'
  },
  Shopping: {
    url: '/images/activity-fallbacks/shopping-01.png',
    alt: 'Lokale Geschäfte und Marktgefühl'
  },
  Nightlife: {
    url: '/images/activity-fallbacks/nightlife-01.png',
    alt: 'Abendliche Stadtatmosphäre mit warmen Lichtern'
  },
  Sport: {
    url: '/images/activity-fallbacks/sport-01.png',
    alt: 'Sportlicher Ort und aktive Stadterfahrung'
  }
}

const fallbackImage = computed<GalleryImage>(() => {
  const fallback = categoryFallbackImages[categoryName.value]
  return {
    url: fallback.url,
    alt: fallback.alt,
    credit: 'TravelMate Standardbild'
  }
})

const propImages = computed(() => {
  const images = props.activity?.activity?.images
  return Array.isArray(images) ? images : []
})

const localImages = computed<ActivityImage[]>(() => [])

const rawImages = computed(() => {
  const merged: ActivityImage[] = []
  const seen = new Set<string>()
  for (const image of [...propImages.value, ...localImages.value]) {
    const url = resolveImageUrl(image?.url || '')
    if (!isUsableImageUrl(url) || seen.has(url)) continue
    seen.add(url)
    merged.push(image)
  }
  return merged
})

const normalizeImage = (image: unknown): GalleryImage | null => {
  if (!image || typeof image !== 'object') return null
  const candidate = image as Partial<ActivityImage>
  const url = resolveImageUrl(candidate.url)
  if (!isUsableImageUrl(url)) return null
  return {
    url,
    alt: String(candidate.alt || `Bild zu ${props.activity?.activity?.name || 'Aktivität'}`),
    credit: candidate.credit ? String(candidate.credit) : undefined,
    source: candidate.source ? String(candidate.source) : undefined
  }
}

const realImages = computed(() => rawImages.value
  .map(normalizeImage)
  .filter((image): image is GalleryImage => Boolean(image)))

const displaySlides = computed<GallerySlide[]>(() => {
  const fallback = fallbackImage.value
  const images = realImages.value

  if (images.length) {
    return images.map((image, index) => ({
      key: `${image.url}-${index}`,
      image: fallback,
      realImage: image,
      fallbackImage: fallback,
      label: image.alt || categoryCopy[categoryName.value][index % categoryCopy[categoryName.value].length],
      variant: (index % 3) + 1
    }))
  }

  return [{
    key: `${props.activity.id}-fallback-${categoryName.value}`,
    image: fallback,
    realImage: null,
    fallbackImage: fallback,
    label: categoryCopy[categoryName.value][0],
    variant: 1
  }]
})

const galleryClass = computed(() => `gallery-${categoryName.value.toLowerCase()}`)
const visibleSlides = computed(() => displaySlides.value)

const clearFailedImageUrl = (url: string) => {
  const timer = failedImageRetryTimers.get(url)
  if (timer) window.clearTimeout(timer)
  failedImageRetryTimers.delete(url)
  failedImageUrls.value = failedImageUrls.value.filter(item => item !== url)
}

const clearFailedImageRetries = () => {
  failedImageRetryTimers.forEach(timer => window.clearTimeout(timer))
  failedImageRetryTimers.clear()
  failedImageUrls.value = []
  imageRetryVersions.value = {}
}

const scheduleFailedImageRetry = (url: string) => {
  const existingTimer = failedImageRetryTimers.get(url)
  if (existingTimer) window.clearTimeout(existingTimer)
  const timer = window.setTimeout(() => {
    failedImageRetryTimers.delete(url)
    failedImageUrls.value = failedImageUrls.value.filter(item => item !== url)
    if (realImages.value.some(image => image.url === url)) {
      imageRetryVersions.value = {
        ...imageRetryVersions.value,
        [url]: (imageRetryVersions.value[url] || 0) + 1
      }
      imageLoadStatus.value = 'loading'
      debugGallery('image-retry-after-error', { url, retryVersion: imageRetryVersions.value[url] })
    }
  }, imageRetryAfterMs)
  failedImageRetryTimers.set(url, timer)
}

watch(() => props.activity.id, () => {
  debugGallery('activity-change-before-reset')
  activeSlide.value = 0
  expanded.value = false
  clearFailedImageRetries()
  loadedImageUrls.value = []
  imageLoadStatus.value = 'idle'
  requestImages()
  debugGallery('activity-change-after-reset')
})

watch(displaySlides, (items) => {
  if (activeSlide.value >= items.length) activeSlide.value = 0
  if (items.some(item => item.realImage)) clearFailedImageRetries()
  debugGallery('display-slides', {
    slideKeys: items.map(item => item.key),
    realSlideCount: items.filter(item => item.realImage).length
  })
})

watch(realImages, (images) => {
  if (!images.length) {
    if (imageLoadStatus.value === 'ready') imageLoadStatus.value = 'idle'
    return
  }
  if (images.some(image => loadedImageUrls.value.includes(image.url))) {
    imageLoadStatus.value = 'ready'
    return
  }
  if (imageLoadStatus.value !== 'failed') imageLoadStatus.value = 'loading'
}, { immediate: true })

const changeSlide = (offset: number) => {
  const count = displaySlides.value.length
  activeSlide.value = (activeSlide.value + offset + count) % count
}

const resolveImageUrl = (url: string) => {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  const base = String(config.public.apiBase || '').replace(/\/$/, '')
  return `${base}${url.startsWith('/') ? url : `/${url}`}`
}

const isUsableImageUrl = (url: string) => {
  if (!url) return false
  if (url.includes('/undefined/') || url.includes('/null/')) return false
  return /^https?:\/\//i.test(url) || url.startsWith('/')
}

const imageSource = (url: string) => {
  const version = imageRetryVersions.value[url] || 0
  if (!version) return url
  if (!url.includes('/activities/') || !url.includes('/images/') || !url.includes('/media')) return url
  return `${url}${url.includes('?') ? '&' : '?'}tmRetry=${version}`
}

const isImageLoaded = (url: string) => loadedImageUrls.value.includes(url)

const requestImages = () => {
  if (!props.activity?.activity?.id) return
  if (realImages.value.length || imageLoadStatus.value === 'loading') return
  imageLoadStatus.value = 'loading'
  debugGallery('request-images')
  emit('requestImages', props.activity.activity.id)
}

const markImageLoaded = (url: string) => {
  debugGallery('image-load', { url })
  clearFailedImageUrl(url)
  if (!loadedImageUrls.value.includes(url)) {
    loadedImageUrls.value = [...loadedImageUrls.value, url]
  }
  imageLoadStatus.value = 'ready'
}

const markImageFailed = (url: string) => {
  debugGallery('image-error', { url, wasLoaded: loadedImageUrls.value.includes(url) })
  if (loadedImageUrls.value.includes(url)) return
  if (!failedImageUrls.value.includes(url)) {
    failedImageUrls.value = [...failedImageUrls.value, url]
  }
  scheduleFailedImageRetry(url)
  if (!realImages.value.some(image => image.url !== url && !failedImageUrls.value.includes(image.url))) {
    imageLoadStatus.value = 'failed'
  }
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'ArrowLeft') changeSlide(-1)
  if (event.key === 'ArrowRight') changeSlide(1)
}

onMounted(() => {
  debugGallery('mount')
  requestImages()
})

onUnmounted(() => {
  clearFailedImageRetries()
  debugGallery('unmount')
})
</script>

<template>
  <section
    class="activity-gallery"
    :class="[galleryClass, { 'is-expanded': expanded }]"
    tabindex="0"
    :aria-label="`Bildergalerie zu ${activity.activity.name}`"
    @keydown="handleKeydown"
  >
    <div class="activity-gallery-stage">
      <template v-for="(slide, index) in visibleSlides" :key="slide.key">
        <figure
          v-show="activeSlide === index"
          class="activity-gallery-slide"
          :class="`placeholder-variant-${slide.variant}`"
          >
          <img
            v-if="!slide.realImage || !isImageLoaded(slide.realImage.url)"
            class="gallery-fallback-image"
            :class="{ 'is-loading-underlay': slide.realImage }"
            :src="slide.fallbackImage.url"
            :alt="slide.fallbackImage.alt"
            loading="eager"
          >
          <img
            v-if="slide.realImage"
            class="gallery-real-image"
            :class="{
              'is-loaded': isImageLoaded(slide.realImage.url),
              'has-load-error': failedImageUrls.includes(slide.realImage.url)
            }"
            :src="imageSource(slide.realImage.url)"
            :alt="slide.realImage.alt || slide.fallbackImage.alt"
            loading="eager"
            decoding="async"
            @load="markImageLoaded(slide.realImage.url)"
            @error="markImageFailed(slide.realImage.url)"
          >

          <figcaption>
            <div>
              <span class="gallery-kicker">{{ categoryName }} · Bild {{ index + 1 }}</span>
              <strong>{{ slide.label }}</strong>
              <small v-if="slide.realImage?.credit">{{ slide.realImage.credit }}</small>
              <small v-else>Bildplatzhalter für {{ activity.activity.name }}</small>
            </div>
              <span class="gallery-counter">{{ index + 1 }} / {{ visibleSlides.length }}</span>
          </figcaption>
        </figure>
      </template>

      <button
        v-if="visibleSlides.length > 1 || expanded"
        class="gallery-arrow gallery-arrow-left"
        type="button"
        :disabled="visibleSlides.length <= 1"
        aria-label="Vorheriges Bild"
        @click="changeSlide(-1)"
      ><ArrowLeft :size="19" /></button>
      <button
        v-if="visibleSlides.length > 1 || expanded"
        class="gallery-arrow gallery-arrow-right"
        type="button"
        :disabled="visibleSlides.length <= 1"
        aria-label="Nächstes Bild"
        @click="changeSlide(1)"
      ><ArrowRight :size="19" /></button>

      <button
        class="gallery-expand-button"
        type="button"
        :aria-pressed="expanded"
        :aria-label="expanded ? 'Bildvorschau verkleinern' : 'Bildvorschau vergrößern'"
        @click.stop="expanded = !expanded"
      >
        <Maximize
          class="gallery-expand-icon"
          :size="24"
          :stroke-width="2.35"
          aria-hidden="true"
        />
      </button>

      <div class="gallery-badge">
        <Camera :size="14" />Galerie
      </div>
    </div>

    <footer class="activity-gallery-footer">
      <div class="gallery-activity-copy">
        <strong>{{ activity.activity.name }}</strong>
        <span v-if="activity.activity.address"><MapPin :size="14" />{{ activity.activity.address }}</span>
        <span v-else><MapPin :size="14" />{{ city }}</span>
      </div>
      <div class="gallery-dots" aria-label="Galeriebilder">
        <button
          v-for="(slide, index) in visibleSlides"
          :key="slide.key"
          type="button"
          :class="{ active: activeSlide === index }"
          :aria-label="`Bild ${index + 1} anzeigen`"
          :aria-current="activeSlide === index ? 'true' : undefined"
          @click="activeSlide = index"
        />
      </div>
    </footer>
  </section>
</template>
