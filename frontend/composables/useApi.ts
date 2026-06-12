export const useApi = () => {
  const config = useRuntimeConfig()
  const { hydrateAuth, token } = useAuth()

  const request = async <T>(path: string, options: any = {}) => {
    hydrateAuth()

    return await $fetch<T>(`${config.public.apiBase}${path}`, {
      ...options,
      headers: {
        ...(options.headers || {}),
        ...(token.value ? { Authorization: `Bearer ${token.value}` } : {})
      }
    })
  }

  return { request, token }
}
