<script setup lang="ts">
import { CalendarDays, LogOut, Moon, Plus, Route, Sun } from 'lucide-vue-next'

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
      <span><strong>TravelMate</strong><small>Planner</small></span>
    </NuxtLink>

    <nav class="app-nav-links" aria-label="Hauptnavigation">
      <NuxtLink to="/calendar"><CalendarDays :size="18" />Kalender</NuxtLink>
      <NuxtLink to="/planner"><Plus :size="18" />Neue Reise</NuxtLink>
      <NuxtLink to="/calendar#reisen"><Route :size="18" />Reisen</NuxtLink>
    </nav>

    <div class="app-nav-actions">
      <span class="app-user">
        <strong>{{ user?.displayName || 'TravelMate' }}</strong>
        <small>{{ user?.email }}</small>
      </span>
      <button class="nav-icon-button" type="button" :title="isDarkMode ? 'Lightmode' : 'Darkmode'" @click="$emit('toggleTheme')">
        <Sun v-if="isDarkMode" :size="18" />
        <Moon v-else :size="18" />
      </button>
      <button class="nav-icon-button" type="button" title="Abmelden" @click="$emit('logout')">
        <LogOut :size="18" />
      </button>
    </div>
  </header>
</template>
