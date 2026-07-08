export default defineNuxtConfig({
  compatibilityDate: '2024-11-01',
  buildDir: process.env.NUXT_BUILD_DIR || '.nuxt',
  css: [
    '~/assets/css/base.css',
    '~/assets/css/planner.css',
    '~/assets/css/schedule.css',
    '~/assets/css/workspace.css',
    '~/assets/css/onboarding.css',
    '~/assets/css/theme-blue.css',
    '~/assets/css/polish.css',
    'leaflet/dist/leaflet.css'
  ],
  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || 'http://localhost:8080',
      mapTileUrl: process.env.NUXT_PUBLIC_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      mapAttribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }
  },
  modules: ['@nuxtjs/google-fonts'],
  googleFonts: {
    families: {
      Inter: [400, 500, 600, 700]
    }
  }
})
