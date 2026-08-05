<script setup lang="ts">
import type { TripDraft } from '~/composables/useTripDraft'

const route = useRoute()
const planner = useTripPlanner()
const tripDraft = useTripDraft()
const preparedDraft = ref<TripDraft | null>(null)
const ready = ref(false)

const completeInterview = async (draft: TripDraft) => {
  tripDraft.saveDraft(draft)
  const created = await planner.createTripFromDraft()
  if (created) await navigateTo(`/trips/${created.id}`)
}

onMounted(async () => {
  await planner.initialize()

  if (route.query.draft === '1') {
    const created = await planner.createTripFromDraft()
    if (created) {
      await navigateTo(`/trips/${created.id}`)
      return
    }
  }

  if (route.query.source === 'calendar') {
    preparedDraft.value = tripDraft.loadDraft()
  }
  ready.value = true
})

onUnmounted(() => {
  planner.cleanupLocationAutocomplete()
})
</script>

<template>
  <div class="planner-journey-page">
    <AppNavigation
      v-if="planner.isLoggedIn.value"
      :user="planner.user.value"
      @logout="planner.logout"
    />

    <main class="planner-journey-main">
      <section v-if="!planner.isLoggedIn.value" class="auth-journey-panel">
        <div class="auth-header">
          <span class="eyebrow">Anmeldung erforderlich</span>
          <h2>Bitte melde dich an</h2>
          <p>Danach kannst du deine neue Reise direkt weiterplanen.</p>
        </div>
        <NuxtLink class="button-link" to="/auth">Zum Login</NuxtLink>
      </section>

      <WelcomeInterview
        v-else-if="ready"
        :initial-draft="preparedDraft"
        :prepared-range="route.query.source === 'calendar'"
        :loading="planner.loading.value"
        :error="planner.error.value"
        @complete="completeInterview"
      />
    </main>

    <TravelPlanningLoadingOverlay :show="planner.loading.value" />
  </div>
</template>
