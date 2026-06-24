<script setup lang="ts">
import { ArrowLeft, LogIn, UserPlus } from 'lucide-vue-next'

type AuthMode = 'login' | 'register'

const route = useRoute()
const { authenticate, hydrateAuth, token } = useAuth()
const { hasDraft } = useTripDraft()

const mode = ref<AuthMode>('login')
const email = ref('')
const password = ref('')
const displayName = ref('')
const error = ref('')
const loading = ref(false)

const title = computed(() => mode.value === 'register' ? 'Account erstellen' : 'Einloggen')
const submitLabel = computed(() => mode.value === 'register' ? 'Account erstellen' : 'Einloggen')
const toggleLabel = computed(() => mode.value === 'register' ? 'Zurück zum Login' : 'Noch keinen Account? Registrieren')

const validate = () => {
  if (!email.value.trim() || !password.value) {
    return 'E-Mail und Passwort sind erforderlich.'
  }

  if (mode.value === 'register' && displayName.value.trim().length < 2) {
    return 'Der Anzeigename muss mindestens 2 Zeichen lang sein.'
  }

  if (mode.value === 'register' && displayName.value.trim() === password.value) {
    return 'Der Anzeigename darf nicht dem Passwort entsprechen.'
  }

  if (mode.value === 'register' && password.value.length < 8) {
    return 'Das Passwort muss mindestens 8 Zeichen lang sein.'
  }

  return ''
}

const submitAuth = async () => {
  error.value = validate()
  if (error.value) return

  loading.value = true
  try {
    const body = mode.value === 'register'
      ? { email: email.value.trim(), password: password.value, displayName: displayName.value.trim() }
      : { email: email.value.trim(), password: password.value }

    await authenticate(mode.value, body)
    await navigateTo(route.query.continue === 'trip' || hasDraft() ? '/planner?draft=1' : '/calendar')
  } catch (err: any) {
    error.value = err?.data?.message || 'Anmeldung fehlgeschlagen. Bitte prüfe deine Eingaben.'
  } finally {
    loading.value = false
  }
}

const toggleMode = () => {
  mode.value = mode.value === 'register' ? 'login' : 'register'
  error.value = ''
}

onMounted(async () => {
  hydrateAuth()
  if (token.value) {
    await navigateTo(route.query.continue === 'trip' || hasDraft() ? '/planner?draft=1' : '/calendar')
  }
})
</script>

<template>
  <div class="auth-journey-page">
    <header class="welcome-nav">
      <NuxtLink class="welcome-brand" to="/">
        <span>T</span><strong>TravelMate</strong>
      </NuxtLink>
      <NuxtLink class="welcome-nav-link" to="/"><ArrowLeft :size="18" />Zur Startseite</NuxtLink>
    </header>

    <main class="auth-journey-main">
      <section class="auth-journey-panel">
        <div class="auth-header">
          <span class="eyebrow">{{ mode === 'register' ? 'Dein TravelMate Konto' : 'Willkommen zurück' }}</span>
          <h2>{{ title }}</h2>
          <p>{{ mode === 'register' ? 'Speichere deine Reise und plane sie jederzeit weiter.' : 'Melde dich an, um deine Reisen und deinen Kalender zu öffnen.' }}</p>
        </div>

        <form class="auth-form" @submit.prevent="submitAuth">
          <label>
            E-Mail
            <input v-model="email" type="email" autocomplete="email" placeholder="name@beispiel.de" required>
          </label>

          <label v-if="mode === 'register'">
            Anzeigename
            <input
              v-model="displayName"
              type="text"
              autocomplete="name"
              minlength="2"
              maxlength="80"
              placeholder="Wie dürfen wir dich nennen?"
              required
            >
          </label>

          <label>
            Passwort
            <input
              v-model="password"
              type="password"
              :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
              placeholder="Mindestens 8 Zeichen"
              required
            >
          </label>

          <p v-if="error" class="error">{{ error }}</p>

          <div class="auth-actions">
            <button class="auth-submit" :disabled="loading" type="submit">
              <UserPlus v-if="mode === 'register'" :size="18" />
              <LogIn v-else :size="18" />
              {{ loading ? 'Bitte warten...' : submitLabel }}
            </button>
            <button class="auth-mode-toggle" type="button" @click="toggleMode">
              {{ toggleLabel }}
            </button>
          </div>
        </form>
      </section>
    </main>
  </div>
</template>
