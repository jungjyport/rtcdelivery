import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type AuthProvider = 'LOCAL' | 'GOOGLE' | 'KAKAO'

export interface AuthUser {
  id: number
  username: string
  nickname: string
  role: string
  email?: string
  authProvider?: AuthProvider
}

export const useAuthStore = defineStore('auth', () => {
  /** 메모리 전용. localStorage / sessionStorage / 쿠키에 저장하지 않는다. */
  const accessToken = ref<string | null>(null)
  const user = ref<AuthUser | null>(null)
  const isRestoring = ref(false)

  let restorePromise: Promise<void> | null = null

  const isLoggedIn = computed(() => accessToken.value !== null)

  const setAuth = (token: string, authUser: AuthUser) => {
    accessToken.value = token
    user.value = authUser
  }

  const setToken = (token: string) => {
    accessToken.value = token
  }

  const clearAuth = () => {
    accessToken.value = null
    user.value = null
  }

  /**
   * 새로고침 후 Access Token(메모리)을 refresh 쿠키로 복구한다.
   * single-flight — 동시 호출 시 같은 Promise를 공유한다.
   * 실패해도 reject 하지 않는다 (비로그인은 정상 상태).
   */
  const restoreSession = (): Promise<void> => {
    if (import.meta.server) return Promise.resolve()
    if (accessToken.value) return Promise.resolve()
    if (restorePromise) return restorePromise

    restorePromise = doRestore().finally(() => {
      restorePromise = null
    })
    return restorePromise
  }

  const doRestore = async (): Promise<void> => {
    isRestoring.value = true
    try {
      const { $api } = useNuxtApp()
      const { accessToken: token } = await $api.post<{ accessToken: string }>(
        '/auth/refresh-token',
        undefined,
        { skipAuth: true, skipRefresh: true },
      )
      setToken(token)
      try {
        user.value = await $api.get<AuthUser>('/auth/me', { skipRefresh: true })
      }
      catch {
        // 토큰 복구는 성공. 프로필은 이후 재시도할 수 있다.
      }
    }
    catch {
      clearAuth()
    }
    finally {
      isRestoring.value = false
    }
  }

  return {
    accessToken,
    user,
    isRestoring,
    isLoggedIn,
    setAuth,
    setToken,
    clearAuth,
    restoreSession,
  }
})
