<script setup lang="ts">
defineProps<{
  user?: {
    displayName?: string
    email?: string
  } | null
}>()

defineEmits<{
  logout: []
}>()
</script>

<template>
  <section class="panel toolbar">
    <div>
      <strong>{{ user?.displayName || user?.email }}</strong>
      <p class="muted">{{ user?.email }}</p>
    </div>
    <button class="secondary" @click="$emit('logout')">Abmelden</button>
  </section>
</template>
