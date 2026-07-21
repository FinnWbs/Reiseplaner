<script setup lang="ts">
import { CalendarDays, LogOut, Plus, Route } from 'lucide-vue-next'

const route = useRoute()
const adminLoginOpen = ref(false)
const adminUsername = ref('')
const adminPassword = ref('')
const adminError = ref('')

defineProps<{
  user?: { displayName?: string; email?: string } | null
}>()

defineEmits<{
  logout: []
}>()

const openAdminLogin = () => {
  adminLoginOpen.value = true
  adminUsername.value = ''
  adminPassword.value = ''
  adminError.value = ''
}

const closeAdminLogin = () => {
  adminLoginOpen.value = false
}

const submitAdminLogin = async () => {
  if (adminUsername.value === 'Admin' && adminPassword.value === 'Admin') {
    if (import.meta.client) sessionStorage.setItem('travelmate-admin-auth', 'true')
    adminLoginOpen.value = false
    await navigateTo('/admin-feedback')
    return
  }
  adminError.value = 'Benutzername oder Passwort ist falsch.'
}
</script>

<template>
  <header class="app-navigation">
    <NuxtLink class="app-brand" to="/calendar" aria-label="TravelMate Kalender">
      <span class="app-brand-mark">T</span>
      <span><strong>TravelMate</strong><small>Reiseplaner</small></span>
    </NuxtLink>

    <nav class="app-nav-links" aria-label="Hauptnavigation">
      <NuxtLink
        to="/calendar"
        active-class="nav-link-match"
        :class="{ 'nav-current': route.path === '/calendar' && route.hash !== '#reisen' }"
      ><CalendarDays :size="18" /><span>Kalender</span></NuxtLink>
      <NuxtLink to="/planner"><Plus :size="18" /><span>Neue Reise</span></NuxtLink>
      <NuxtLink
        to="/calendar#reisen"
        active-class="nav-link-match"
        :class="{ 'nav-current': route.path === '/calendar' && route.hash === '#reisen' }"
      ><Route :size="18" /><span>Meine Reisen</span></NuxtLink>
    </nav>

    <div class="app-nav-actions">
      <button
        class="app-user-avatar app-user-avatar-button"
        type="button"
        title="Adminbereich"
        aria-label="Adminbereich oeffnen"
        @click="openAdminLogin"
      >
        {{ (user?.displayName || user?.email || 'T').charAt(0).toUpperCase() }}
      </button>
      <span class="app-user">
        <strong>{{ user?.displayName || 'TravelMate' }}</strong>
        <small>{{ user?.email }}</small>
      </span>
      <button class="nav-icon-button" type="button" title="Abmelden" aria-label="Abmelden" @click="$emit('logout')">
        <LogOut :size="18" />
      </button>
    </div>

    <Teleport to="body">
      <div v-if="adminLoginOpen" class="admin-login-backdrop" @click.self="closeAdminLogin">
        <form class="admin-login-dialog" @submit.prevent="submitAdminLogin">
          <button class="admin-login-close" type="button" aria-label="Schliessen" @click="closeAdminLogin">x</button>
          <span class="eyebrow">Admin</span>
          <h2>Feedbackbereich</h2>
          <p>Melde dich an, um die abgegebenen Feedbacks einzusehen.</p>
          <label>
            <span>Benutzername</span>
            <input v-model="adminUsername" autocomplete="username" placeholder="Admin">
          </label>
          <label>
            <span>Passwort</span>
            <input v-model="adminPassword" type="password" autocomplete="current-password" placeholder="Admin">
          </label>
          <p v-if="adminError" class="admin-login-error">{{ adminError }}</p>
          <button class="admin-login-submit" type="submit">Anmelden</button>
        </form>
      </div>
    </Teleport>
  </header>
</template>
