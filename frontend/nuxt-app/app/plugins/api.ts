import { useAuthStore } from '~/stores/authStore'
import { createApiClient } from '~/utils/api-client'

export default defineNuxtPlugin({
  name: 'api',
  setup(nuxtApp) {
    const config = useRuntimeConfig()
    const authStore = useAuthStore()
    const router = useRouter()

    const baseURL = import.meta.server
      ? ((config.apiBaseUrl as string) || config.public.apiBaseUrl)
      : config.public.apiBaseUrl

    const api = createApiClient({
      baseURL,
      getAccessToken: () => authStore.accessToken,
      setAccessToken: token => authStore.setToken(token),
      // 플러그인 setup 안에서는 useI18n()을 쓸 수 없으므로 nuxtApp.$i18n을 사용
      getLocale: () => nuxtApp.$i18n?.locale?.value ?? 'ko',
      isServer: import.meta.server,
      onLogout: async () => {
        authStore.clearAuth()
        await nuxtApp.runWithContext(() => {
          if (router.currentRoute.value.path === '/auth/login') return
          return navigateTo('/auth/login')
        })
      },
    })

    return {
      provide: {
        api,
      },
    }
  },
})
