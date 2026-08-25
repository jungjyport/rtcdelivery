import { useAuthStore } from "~/stores/authStore";

export default defineNuxtRouteMiddleware(async () => {
  if (import.meta.server) return;

  const auth = useAuthStore();
  if (!auth.isLoggedIn) await auth.restoreSession();
  if (!auth.isLoggedIn) return navigateTo("/auth/login");
});
