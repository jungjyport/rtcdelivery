import { useAuthStore } from '~/stores/authStore'

export default defineNuxtRouteMiddleware(async (to) => {
  if (import.meta.server) return

  const authStore = useAuthStore()
  await authStore.restoreSession()

  if (!authStore.isLoggedIn) {
    return navigateTo({
      path: '/auth/login',
      query: { redirect: to.fullPath },
    })
  }

  const role = authStore.user?.role
  if (role !== 'ROLE_OWNER' && role !== 'ROLE_ADMIN') {
    return navigateTo('/')
  }
})
