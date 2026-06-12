<script setup lang="ts">
type AuthMode = 'login' | 'register'

const { authenticate, hydrateAuth, token } = useAuth()

const mode = ref<AuthMode>('register')
const email = ref('')
const password = ref('')
const displayName = ref('')
const error = ref('')
const loading = ref(false)

const title = computed(() => mode.value === 'register' ? 'Account erstellen' : 'Einloggen')
const submitLabel = computed(() => mode.value === 'register' ? 'Account erstellen' : 'Einloggen')
const toggleLabel = computed(() => mode.value === 'register' ? 'Ich habe schon einen Account' : 'Neuen Account erstellen')

const validate = () => {
  if (!email.value.trim() || !password.value) {
    return 'E-Mail und Passwort sind erforderlich.'
  }

  if (mode.value === 'register' && !displayName.value.trim()) {
    return 'Bitte gib einen Anzeigenamen ein.'
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
    await navigateTo('/')
  } catch (err: any) {
    error.value = err?.data?.message || 'Anmeldung fehlgeschlagen. Bitte pruefe deine Eingaben.'
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
    await navigateTo('/')
  }
})
</script>

<template>
  <div class="page auth-page">
    <aside class="sidebar">
      <h1>TravelMate Planner</h1>
      <p>Plane Staedtereisen mit wenigen Angaben und gespeicherten Interessen.</p>
      <p class="muted">Erstelle einen Account oder melde dich an, um deine Reisen weiterzubearbeiten.</p>
    </aside>

    <main class="main auth-main">
      <section class="panel auth-panel grid">
        <div class="auth-header">
          <span class="eyebrow">{{ mode === 'register' ? 'Neu starten' : 'Willkommen zurueck' }}</span>
          <h2>{{ title }}</h2>
        </div>

        <form class="grid" @submit.prevent="submitAuth">
          <label>
            E-Mail
            <input v-model="email" type="email" autocomplete="email" required>
          </label>

          <label>
            Passwort
            <input
              v-model="password"
              type="password"
              :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
              required
            >
          </label>

          <label v-if="mode === 'register'">
            Anzeigename
            <input v-model="displayName" type="text" autocomplete="name" required>
          </label>

          <p v-if="error" class="error">{{ error }}</p>

          <div class="actions auth-actions">
            <button :disabled="loading" type="submit">
              {{ loading ? 'Bitte warten...' : submitLabel }}
            </button>
            <button class="secondary" type="button" @click="toggleMode">
              {{ toggleLabel }}
            </button>
          </div>
        </form>
      </section>
    </main>
  </div>
</template>
