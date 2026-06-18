export const usePlannerTheme = () => {
  const isDarkMode = ref(false)
  const isAtPageEnd = ref(false)
  const pageEnd = ref<HTMLElement | null>(null)

  const applyTheme = () => {
    if (!import.meta.client) return
    document.documentElement.classList.toggle('dark-mode', isDarkMode.value)
    sessionStorage.setItem('travelmate-theme', isDarkMode.value ? 'dark' : 'light')
  }

  const toggleTheme = () => {
    isDarkMode.value = !isDarkMode.value
    applyTheme()
  }

  const updateScrollTarget = () => {
    if (!import.meta.client) return
    const maxScroll = document.documentElement.scrollHeight - window.innerHeight
    isAtPageEnd.value = window.scrollY >= maxScroll - 24
  }

  const scrollToPageEdge = () => {
    if (isAtPageEnd.value) {
      window.scrollTo({ top: 0, behavior: 'smooth' })
      return
    }
    pageEnd.value?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }

  const initPlannerTheme = () => {
    if (!import.meta.client) return
    isDarkMode.value = sessionStorage.getItem('travelmate-theme') === 'dark'
    applyTheme()
    updateScrollTarget()
    window.addEventListener('scroll', updateScrollTarget, { passive: true })
    window.addEventListener('resize', updateScrollTarget)
  }

  const cleanupPlannerTheme = () => {
    if (!import.meta.client) return
    window.removeEventListener('scroll', updateScrollTarget)
    window.removeEventListener('resize', updateScrollTarget)
  }

  return {
    isDarkMode,
    isAtPageEnd,
    pageEnd,
    toggleTheme,
    scrollToPageEdge,
    updateScrollTarget,
    initPlannerTheme,
    cleanupPlannerTheme
  }
}
