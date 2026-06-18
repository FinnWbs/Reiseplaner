<script setup lang="ts">
import { ArrowDownToLine, ArrowUpToLine, Moon, Sun } from 'lucide-vue-next'

defineProps<{
  isDarkMode: boolean
  isAtPageEnd: boolean
}>()

defineEmits<{
  toggleTheme: []
  scrollToEdge: []
}>()
</script>

<template>
  <div class="floating-controls" aria-label="Schnellaktionen">
    <button
      class="floating-button"
      type="button"
      :title="isDarkMode ? 'Lightmode aktivieren' : 'Darkmode aktivieren'"
      @click="$emit('toggleTheme')"
    >
      <Sun v-if="isDarkMode" :size="19" />
      <Moon v-else :size="19" />
    </button>
    <button
      class="floating-button"
      type="button"
      :title="isAtPageEnd ? 'Nach oben springen' : 'Nach unten springen'"
      @click="$emit('scrollToEdge')"
    >
      <ArrowUpToLine v-if="isAtPageEnd" :size="19" />
      <ArrowDownToLine v-else :size="19" />
    </button>
  </div>
</template>
