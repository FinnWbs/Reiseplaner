export default defineNuxtConfig({
  compatibilityDate: '2024-11-01',
  buildDir: process.env.NUXT_BUILD_DIR || '.nuxt',
  css: [
    '~/assets/css/base.css',
    '~/assets/css/planner.css',
    '~/assets/css/schedule.css',
    '~/assets/css/dark.css'
  ],
  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || 'http://localhost:8080'
    }
  },
  modules: ['@nuxtjs/google-fonts'],
  googleFonts: {
    families: {
      Inter: [400, 500, 600, 700]
    }
  }
})
