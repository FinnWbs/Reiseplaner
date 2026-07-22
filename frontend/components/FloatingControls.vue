<script setup lang="ts">
import { ArrowDownToLine, ArrowUpToLine } from 'lucide-vue-next'

defineProps<{
  isAtPageEnd: boolean
}>()

defineEmits<{
  scrollToEdge: []
}>()
</script>

<template>
  <div class="floating-controls" aria-label="Schnellaktionen">
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
