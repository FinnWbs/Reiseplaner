<template>
  <Teleport to="body">
    <div
      v-if="show"
      class="travel-loading-overlay"
      role="status"
      aria-live="polite"
      aria-busy="true"
    >
      <span class="travel-loading-sr-only">Die Reiseplanung laeuft. Bitte warten.</span>
      <div class="travel-loading-panel" aria-hidden="true">
        <span class="travel-loading-spinner" />
        <p class="travel-loading-text">Deine Reise wird geplant &hellip;</p>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
defineProps<{
  show: boolean
}>()
</script>

<style scoped>
.travel-loading-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(244, 248, 252, 0.78);
  backdrop-filter: blur(10px);
  pointer-events: all;
}

.travel-loading-panel {
  display: grid;
  justify-items: center;
  gap: 20px;
  width: min(100%, 460px);
  padding: clamp(32px, 6vw, 48px);
  border: 1px solid rgba(203, 217, 232, 0.88);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 24px 64px rgba(11, 47, 91, 0.18);
}

.travel-loading-spinner {
  width: 52px;
  height: 52px;
  border: 5px solid rgba(47, 133, 207, 0.2);
  border-top-color: var(--brand-primary);
  border-radius: 50%;
  animation: travel-loading-spin 800ms linear infinite;
  transform: translateZ(0);
  will-change: transform;
}

.travel-loading-text {
  margin: 0;
  color: var(--brand-text);
  font-size: clamp(17px, 4vw, 21px);
  font-weight: 800;
  text-align: center;
}

.travel-loading-sr-only {
  position: absolute;
  overflow: hidden;
  width: 1px;
  height: 1px;
  padding: 0;
  border: 0;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

@keyframes travel-loading-spin {
  from { transform: translateZ(0) rotate(0deg); }
  to { transform: translateZ(0) rotate(360deg); }
}

@media (max-width: 520px) {
  .travel-loading-overlay {
    padding: 16px;
  }

  .travel-loading-panel {
    padding: 32px 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .travel-loading-spinner {
    animation-duration: 1.6s;
  }
}
</style>
