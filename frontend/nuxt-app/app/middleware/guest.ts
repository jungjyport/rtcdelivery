import { useAuthStore } from '~/stores/authStore'
import { getSafeRedirect } from '~/utils/auth-validation'

export default defineNuxtRouteMiddleware(async (to) => {
  if (import.meta.server) return

  const auth = useAuthStore()
  await auth.restoreSession()
  if (!auth.isLoggedIn) return

  const redirect = getSafeRedirect(to.query.redirect)
  return navigateTo(redirect ?? '/')
})
