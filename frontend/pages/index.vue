<script setup lang="ts">
import { CalendarDays, LogIn } from 'lucide-vue-next'

const { hydrateAuth, token } = useAuth()
const { saveDraft } = useTripDraft()

onMounted(() => {
  hydrateAuth()
})

const completeInterview = async (draft: Parameters<typeof saveDraft>[0]) => {
  saveDraft(draft)
  await navigateTo(token.value ? '/planner?draft=1' : '/auth?continue=trip')
}
</script>

<template>
  <div class="onboarding-page">
    <header class="welcome-nav">
      <NuxtLink class="welcome-brand" to="/">
        <span>T</span><strong>TravelMate</strong>
      </NuxtLink>
      <NuxtLink v-if="token" class="welcome-nav-link" to="/calendar"><CalendarDays :size="18" />Zum Kalender</NuxtLink>
      <NuxtLink v-else class="welcome-nav-link" to="/auth"><LogIn :size="18" />Einloggen</NuxtLink>
    </header>

    <main class="welcome-main">
      <section class="welcome-copy">
        <span class="welcome-eyebrow">Persönliche Städtereisen</span>
        <h1>Deine Reise.<br><span>Dein Rhythmus.</span></h1>
        <p>Von der ersten Idee bis zum fertigen Tagesplan – persönlich, übersichtlich und ohne Planungschaos.</p>
      </section>
      <WelcomeInterview @complete="completeInterview" />
    </main>
  </div>
</template>
