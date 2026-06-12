type AuthMode = 'login' | 'register'

type AuthResponse = {
  token: string
  userId: number
  email: string
  displayName: string
  role: string
}

type AuthUser = {
  userId: number
  email: string
  displayName: string
  role: string
}

const authStorageKey = 'travelmate-auth'

export const useAuth = () => {
  const config = useRuntimeConfig()
  const token = useState<string | null>('auth-token', () => null)
  const user = useState<AuthUser | null>('auth-user', () => null)

  const storeAuth = (response: AuthResponse) => {
    token.value = response.token
    user.value = {
      userId: response.userId,
      email: response.email,
      displayName: response.displayName,
      role: response.role
    }

    if (import.meta.client) {
      sessionStorage.setItem(authStorageKey, JSON.stringify({ token: token.value, user: user.value }))
    }
  }

  const hydrateAuth = () => {
    if (!import.meta.client || token.value) return

    const raw = sessionStorage.getItem(authStorageKey)
    if (!raw) return

    try {
      const stored = JSON.parse(raw) as { token?: string; user?: AuthUser }
      token.value = stored.token || null
      user.value = stored.user || null
    } catch {
      sessionStorage.removeItem(authStorageKey)
    }
  }

  const clearAuth = () => {
    token.value = null
    user.value = null

    if (import.meta.client) {
      sessionStorage.removeItem(authStorageKey)
    }
  }

  const authenticate = async (mode: AuthMode, body: Record<string, string>) => {
    const response = await $fetch<AuthResponse>(`${config.public.apiBase}/auth/${mode}`, {
      method: 'POST',
      body
    })
    storeAuth(response)
    return response
  }

  return { authenticate, clearAuth, hydrateAuth, token, user }
}
