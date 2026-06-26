<script setup lang="ts">
import { CalendarDays, LogOut, Moon, Plus, Route, Sun } from 'lucide-vue-next'

const route = useRoute()

defineProps<{
  user?: { displayName?: string; email?: string } | null
  isDarkMode: boolean
}>()

defineEmits<{
  logout: []
  toggleTheme: []
}>()
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
      <span class="app-user-avatar" aria-hidden="true">{{ (user?.displayName || user?.email || 'T').charAt(0).toUpperCase() }}</span>
      <span class="app-user">
        <strong>{{ user?.displayName || 'TravelMate' }}</strong>
        <small>{{ user?.email }}</small>
      </span>
      <button class="nav-icon-button" type="button" :title="isDarkMode ? 'Helles Design' : 'Dunkles Design'" :aria-label="isDarkMode ? 'Helles Design aktivieren' : 'Dunkles Design aktivieren'" @click="$emit('toggleTheme')">
        <Sun v-if="isDarkMode" :size="18" />
        <Moon v-else :size="18" />
      </button>
      <button class="nav-icon-button" type="button" title="Abmelden" aria-label="Abmelden" @click="$emit('logout')">
        <LogOut :size="18" />
      </button>
    </div>
  </header>
</template>
