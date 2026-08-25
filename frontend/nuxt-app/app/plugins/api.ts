import { useAuthStore } from '~/stores/authStore'
import { createApiClient } from '~/utils/api-client'

export default defineNuxtPlugin({
  name: 'api',
  setup(nuxtApp) {
    const config = useRuntimeConfig()
    const authStore = useAuthStore()
    const { locale } = useI18n()

    const api = createApiClient({
      baseURL: config.public.apiBaseUrl,
      getAccessToken: () => authStore.accessToken,
      setAccessToken: token => authStore.setToken(token),
      getLocale: () => locale.value,
      isServer: import.meta.server,
      onLogout: async () => {
        authStore.clearAuth()
        await nuxtApp.runWithContext(() => {
          if (useRoute().path === '/auth/login') return
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
