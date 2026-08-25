import { useAuthStore } from "~/stores/authStore"

export default defineNuxtPlugin({
  name: 'auth-restore',
  dependsOn: ['api'],
  setup() {
    const authStore = useAuthStore()
    void authStore.restoreSession()
  },
})
