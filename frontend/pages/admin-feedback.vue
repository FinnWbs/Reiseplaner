<script setup lang="ts">
import { ArrowLeft, ImageOff } from 'lucide-vue-next'

type FeedbackEntry = {
  id: number
  userEmail: string
  pageUrl?: string
  targetLabel?: string
  targetSelector?: string
  screenshotDataUrl?: string
  description: string
  createdAt: string
}

const { request } = useApi()
const { hydrateAuth, user, clearAuth } = useAuth()
const loading = ref(true)
const error = ref('')
const feedbacks = ref<FeedbackEntry[]>([])
const selectedId = ref<number | null>(null)
const selectedFeedback = computed(() =>
  feedbacks.value.find(item => item.id === selectedId.value) || feedbacks.value[0] || null
)

const logout = async () => {
  clearAuth()
  await navigateTo('/auth')
}

onMounted(async () => {
  hydrateAuth()
  if (sessionStorage.getItem('travelmate-admin-auth') !== 'true') {
    await navigateTo('/calendar')
    return
  }
  try {
    feedbacks.value = await request<FeedbackEntry[]>('/feedback')
    selectedId.value = feedbacks.value[0]?.id || null
  } catch (err: any) {
    error.value = err?.data?.message || 'Feedbacks konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="workspace-page admin-feedback-page">
    <AppNavigation :user="user" @logout="logout" />

    <main class="admin-feedback-main">
      <NuxtLink class="admin-feedback-back" to="/calendar">
        <ArrowLeft :size="17" />
        Zurueck
      </NuxtLink>

      <header class="admin-feedback-header">
        <span class="eyebrow">Adminbereich</span>
        <h1>Feedbacks</h1>
        <p>Links findest du alle Meldungen. Rechts siehst du den markierten Bereich als Screenshot.</p>
      </header>

      <section v-if="loading" class="calendar-loading">
        <span />
        <p>Feedbacks werden geladen...</p>
      </section>

      <p v-else-if="error" class="error workspace-error">{{ error }}</p>

      <section v-else class="admin-feedback-layout">
        <aside class="admin-feedback-list" aria-label="Feedbacktexte">
          <button
            v-for="item in feedbacks"
            :key="item.id"
            type="button"
            :class="{ active: selectedFeedback?.id === item.id }"
            @click="selectedId = item.id"
          >
            <strong>{{ item.targetLabel || 'Direktes Feedback' }}</strong>
            <span>{{ item.description }}</span>
            <small>{{ new Date(item.createdAt).toLocaleString('de-DE') }} · {{ item.userEmail }}</small>
          </button>
          <p v-if="feedbacks.length === 0" class="admin-feedback-empty">Noch kein Feedback vorhanden.</p>
        </aside>

        <article class="admin-feedback-detail">
          <template v-if="selectedFeedback">
            <div>
              <span class="eyebrow">Feedback</span>
              <h2>{{ selectedFeedback.targetLabel || 'Direktes Feedback' }}</h2>
              <p>{{ selectedFeedback.description }}</p>
              <small>{{ selectedFeedback.pageUrl }}</small>
            </div>
            <figure v-if="selectedFeedback.screenshotDataUrl">
              <img :src="selectedFeedback.screenshotDataUrl" alt="Screenshot des markierten Bereichs">
            </figure>
            <div v-else class="admin-feedback-no-image">
              <ImageOff :size="34" />
              <span>Kein Screenshot vorhanden</span>
            </div>
          </template>
        </article>
      </section>
    </main>
  </div>
</template>
