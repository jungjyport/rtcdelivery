<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useAuthStore } from '~/stores/authStore'
import LocaleSwitcher from '~/components/common/LocaleSwitcher.vue'

const { t } = useI18n()
const authStore = useAuthStore()
const { isRestoring, isLoggedIn, user } = storeToRefs(authStore)
const { logout, isSubmitting } = useAuth()

async function onLogout() {
  if (isSubmitting.value) return
  await logout()
}
</script>

<template>
  <header class="fixed top-0 left-0 right-0 z-50">
    <nav class="bg-white/70 backdrop-blur-xl border-b border-white/10 shadow-lg">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between h-16">
          <NuxtLink to="/" class="flex items-center gap-2">
            <div class="w-9 h-9 bg-gradient-to-br from-primary-500 to-accent-500 rounded-xl flex items-center justify-center shadow-lg shadow-primary-500/20">
              <span class="text-white text-lg" aria-hidden="true">🔥</span>
            </div>
            <span class="text-xl font-bold bg-gradient-to-r from-primary-600 to-accent-500 bg-clip-text text-transparent">
              RTC Delivery
            </span>
          </NuxtLink>

          <div class="hidden md:flex items-center gap-8">
            <NuxtLink
              to="/"
              class="text-surface-600 hover:text-primary-500 font-medium transition-colors duration-200"
            >
              {{ t('nav.home') }}
            </NuxtLink>
            <a href="#" class="text-surface-600 hover:text-primary-500 font-medium transition-colors duration-200">
              {{ t('nav.findRestaurants') }}
            </a>
            <a href="#" class="text-surface-600 hover:text-primary-500 font-medium transition-colors duration-200">
              {{ t('nav.orderHistory') }}
            </a>
          </div>

          <div class="flex items-center gap-3">
            <LocaleSwitcher />

            <div :aria-label="t('header.authSlot')" :aria-busy="isRestoring">
              <div
                v-if="isRestoring"
                class="flex items-center gap-3"
                aria-hidden="true"
              >
                <div class="hidden sm:block h-5 w-14 animate-pulse rounded bg-surface-200" />
                <div class="h-9 w-20 animate-pulse rounded-lg bg-surface-200" />
              </div>

              <div
                v-else-if="isLoggedIn"
                class="flex items-center gap-3"
              >
                <span
                  v-if="user?.nickname"
                  class="hidden sm:inline max-w-[8rem] truncate text-sm font-medium text-surface-700"
                >
                  {{ t('header.greeting', { name: user.nickname }) }}
                </span>
                <button
                  type="button"
                  class="text-surface-600 hover:text-primary-500 font-medium transition-colors duration-200 text-sm disabled:opacity-60"
                  :disabled="isSubmitting"
                  :aria-busy="isSubmitting"
                  @click="onLogout"
                >
                  {{ t('common.logout') }}
                </button>
              </div>

              <div
                v-else
                class="flex items-center gap-3"
              >
                <NuxtLink
                  to="/auth/login"
                  class="hidden sm:inline-flex text-surface-600 hover:text-primary-500 font-medium transition-colors duration-200"
                >
                  {{ t('common.login') }}
                </NuxtLink>
                <NuxtLink
                  to="/auth/signup"
                  class="btn-primary text-sm !px-4 !py-2 !rounded-lg"
                >
                  {{ t('common.signup') }}
                </NuxtLink>
              </div>
            </div>
          </div>
        </div>
      </div>
    </nav>
  </header>
</template>
