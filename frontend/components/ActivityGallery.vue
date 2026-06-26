<script setup lang="ts">
import {
  ArrowLeft,
  ArrowRight,
  Camera,
  Dumbbell,
  Landmark,
  MapPin,
  Moon,
  Palette,
  ShoppingBag,
  Trees,
  Utensils
} from 'lucide-vue-next'
import type { Component } from 'vue'
import type { TripActivity } from '~/types/trip'

type GalleryCategory = 'Kultur' | 'Geschichte' | 'Natur' | 'Food' | 'Shopping' | 'Nightlife' | 'Sport'

const props = defineProps<{
  activity: TripActivity
  city: string
}>()

const activeSlide = ref(0)

const categoryName = computed<GalleryCategory>(() => {
  const value = `${props.activity.activity.category || ''} ${props.activity.activity.subcategory || ''}`.toLowerCase()
  if (/night|club|bar|pub/.test(value)) return 'Nightlife'
  if (/food|restaurant|cafe|market|catering/.test(value)) return 'Food'
  if (/park|natur|garden|forest|beach/.test(value)) return 'Natur'
  if (/shop|commercial|mall/.test(value)) return 'Shopping'
  if (/sport|stadium|fitness/.test(value)) return 'Sport'
  if (/heritage|historic|monument|castle|geschichte/.test(value)) return 'Geschichte'
  return 'Kultur'
})

const categoryIcon = computed<Component>(() => ({
  Kultur: Palette,
  Geschichte: Landmark,
  Natur: Trees,
  Food: Utensils,
  Shopping: ShoppingBag,
  Nightlife: Moon,
  Sport: Dumbbell
})[categoryName.value])

const categoryCopy: Record<GalleryCategory, string[]> = {
  Kultur: ['Kunst & Atmosphäre', 'Details entdecken', 'Ein Ort mit Charakter'],
  Geschichte: ['Spuren der Vergangenheit', 'Architektur im Detail', 'Geschichten vor Ort'],
  Natur: ['Naturerlebnis', 'Neue Perspektiven', 'Zeit zum Durchatmen'],
  Food: ['Geschmack der Stadt', 'Frisch serviert', 'Genuss im Detail'],
  Shopping: ['Lokale Entdeckungen', 'Design & Auswahl', 'Besondere Fundstücke'],
  Nightlife: ['Abendstimmung', 'Lichter der Stadt', 'Nachtleben entdecken'],
  Sport: ['Bewegung erleben', 'Dynamische Momente', 'Aktiv unterwegs']
}

const slides = computed(() => {
  const images = props.activity.activity.images || []
  if (images.length) {
    return images.map((image, index) => ({
      key: `${image.url}-${index}`,
      image,
      label: image.alt,
      variant: (index % 3) + 1
    }))
  }

  return categoryCopy[categoryName.value].map((label, index) => ({
    key: `${props.activity.id}-${index}`,
    image: null,
    label,
    variant: index + 1
  }))
})

watch(() => props.activity.id, () => {
  activeSlide.value = 0
})

watch(slides, (items) => {
  if (activeSlide.value >= items.length) activeSlide.value = 0
})

const changeSlide = (offset: number) => {
  const count = slides.value.length
  activeSlide.value = (activeSlide.value + offset + count) % count
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'ArrowLeft') changeSlide(-1)
  if (event.key === 'ArrowRight') changeSlide(1)
}
</script>

<template>
  <section
    class="activity-gallery"
    :class="`gallery-${categoryName.toLowerCase()}`"
    tabindex="0"
    :aria-label="`Bildergalerie zu ${activity.activity.name}`"
    @keydown="handleKeydown"
  >
    <div class="activity-gallery-stage">
      <template v-for="(slide, index) in slides" :key="slide.key">
        <figure
          v-show="activeSlide === index"
          class="activity-gallery-slide"
          :class="`placeholder-variant-${slide.variant}`"
        >
          <img
            v-if="slide.image"
            :src="slide.image.url"
            :alt="slide.image.alt"
          >
          <div v-else class="gallery-placeholder" aria-hidden="true">
            <span class="gallery-shape gallery-shape-one" />
            <span class="gallery-shape gallery-shape-two" />
            <span class="gallery-shape gallery-shape-three" />
            <component :is="categoryIcon" :size="58" :stroke-width="1.35" />
          </div>

          <figcaption>
            <div>
              <span class="gallery-kicker">{{ categoryName }} · Bild {{ index + 1 }}</span>
              <strong>{{ slide.label }}</strong>
              <small v-if="slide.image?.credit">{{ slide.image.credit }}</small>
              <small v-else>Bildplatzhalter für {{ activity.activity.name }}</small>
            </div>
            <span class="gallery-counter">{{ index + 1 }} / {{ slides.length }}</span>
          </figcaption>
        </figure>
      </template>

      <button
        class="gallery-arrow gallery-arrow-left"
        type="button"
        aria-label="Vorheriges Bild"
        @click="changeSlide(-1)"
      ><ArrowLeft :size="19" /></button>
      <button
        class="gallery-arrow gallery-arrow-right"
        type="button"
        aria-label="Nächstes Bild"
        @click="changeSlide(1)"
      ><ArrowRight :size="19" /></button>

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
          v-for="(slide, index) in slides"
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
