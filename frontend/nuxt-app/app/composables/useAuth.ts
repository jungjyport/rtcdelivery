import { ApiError } from '~/types/api'
import type { LoginRequest, LoginResult, SignupRequest } from '~/types'
import { useAuthStore } from '~/stores/authStore'
import { getSafeRedirect } from '~/utils/auth-validation'

export function useAuth() {
  const isSubmitting = ref(false)
  const formError = ref<string | null>(null)

  const { $api } = useNuxtApp()
  const authStore = useAuthStore()
  const route = useRoute()

  async function login(payload: LoginRequest): Promise<void> {
    formError.value = null
    isSubmitting.value = true
    try {
      const result = await $api.post<LoginResult>('/auth/login', payload, {
        skipAuth: true,
        skipRefresh: true,
      })
      authStore.setAuth(result.accessToken, result.user)
    }
    catch (error) {
      formError.value = i18nKeyFrom(error)
      throw error
    }
    finally {
      isSubmitting.value = false
    }

    const redirect = getSafeRedirect(route.query.redirect)
    await navigateTo(redirect ?? '/')
  }

  async function signup(payload: SignupRequest): Promise<void> {
    formError.value = null
    isSubmitting.value = true
    try {
      await $api.post('/auth/signup', payload, {
        skipAuth: true,
        skipRefresh: true,
      })
    }
    catch (error) {
      formError.value = i18nKeyFrom(error)
      throw error
    }
    finally {
      isSubmitting.value = false
    }

    await navigateTo({ path: '/auth/login', query: { registered: '1' } })
  }

  async function logout(): Promise<void> {
    isSubmitting.value = true
    try {
      await $api.post('/auth/logout', undefined, {
        skipAuth: true,
        skipRefresh: true,
      })
    }
    catch {
      // 네트워크 오류여도 클라이언트 세션은 지운다.
    }
    finally {
      authStore.clearAuth()
      isSubmitting.value = false
    }

    await navigateTo('/auth/login')
  }

  return {
    isSubmitting,
    formError,
    login,
    signup,
    logout,
  }
}

function i18nKeyFrom(error: unknown): string {
  return error instanceof ApiError ? error.i18nKey : 'error.UNKNOWN_ERROR'
}
