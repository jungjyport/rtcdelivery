<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useAuthStore } from '~/stores/authStore'
import type { MemberProfile } from '~/types'

definePageMeta({
  middleware: 'auth',
})

const { t, te, locale } = useI18n()
const authStore = useAuthStore()
const { user: storedUser } = storeToRefs(authStore)
const { logout, isSubmitting: isLoggingOut } = useAuth()
const { $api } = useNuxtApp()

const profile = ref<MemberProfile | null>(null)
const isLoading = ref(true)
const fetchError = ref<string | null>(null)
const showOrderModal = ref(false)
const showSecurityModal = ref(false)

useHead({
  title: () => `${t('mypage.title')} - RTC Delivery`,
  meta: [
    {
      name: 'description',
      content: () => t('mypage.subtitle'),
    },
  ],
})

async function fetchProfile() {
  isLoading.value = true
  fetchError.value = null
  try {
    const data = await $api.get<MemberProfile>('/auth/me')
    profile.value = data
    // authStore의 user 정보도 최신화
    if (data) {
      authStore.setAuth(authStore.accessToken ?? '', {
        id: data.id,
        username: data.username,
        nickname: data.nickname,
        role: data.role,
        email: data.email,
        authProvider: data.authProvider,
        createdAt: data.createdAt,
      })
    }
  }
  catch (err: any) {
    fetchError.value = err?.message || t('mypage.errorLoading')
    // API 조회 실패 시 스토어에 보관된 사용자 기본 정보 폴백
    if (storedUser.value) {
      profile.value = {
        id: storedUser.value.id,
        username: storedUser.value.username,
        nickname: storedUser.value.nickname,
        role: storedUser.value.role,
        email: storedUser.value.email,
        authProvider: storedUser.value.authProvider ?? 'LOCAL',
        createdAt: storedUser.value.createdAt ?? '',
      }
    }
  }
  finally {
    isLoading.value = false
  }
}

onMounted(() => {
  fetchProfile()
})

const displayUser = computed(() => profile.value || storedUser.value)

function formatDate(dateStr?: string) {
  if (!dateStr) return t('mypage.notSet')
  try {
    const d = new Date(dateStr)
    if (isNaN(d.getTime())) return dateStr
    return new Intl.DateTimeFormat(locale.value === 'ja' ? 'ja-JP' : 'ko-KR', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(d)
  }
  catch {
    return dateStr
  }
}

function roleLabel(role?: string) {
  if (!role) return ''
  return te(`mypage.roles.${role}`) ? t(`mypage.roles.${role}`) : role
}

function providerLabel(provider?: string) {
  if (!provider) return ''
  return te(`mypage.providers.${provider}`) ? t(`mypage.providers.${provider}`) : provider
}

async function handleLogout() {
  if (isLoggingOut.value) return
  await logout()
}
</script>

<template>
  <div class="min-h-[calc(100vh-4rem)] pt-20 pb-16 bg-gradient-to-b from-surface-100/60 via-surface-50 to-surface-100/40">
    <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
      
      <!-- Top Breadcrumb & Page Header -->
      <div class="mb-8 animate-fadeInUp">
        <div class="flex items-center gap-2 text-sm text-surface-500 mb-2">
          <NuxtLink to="/" class="hover:text-primary-600 transition-colors">{{ t('common.home') }}</NuxtLink>
          <span>/</span>
          <span class="text-surface-800 font-medium">{{ t('mypage.title') }}</span>
        </div>
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 class="text-3xl sm:text-4xl font-black text-surface-900 tracking-tight">
              {{ t('mypage.title') }}
            </h1>
            <p class="text-surface-500 text-sm sm:text-base mt-1">
              {{ t('mypage.subtitle') }}
            </p>
          </div>
          <button
            v-if="!isLoading"
            type="button"
            class="inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-medium text-surface-600 bg-white border border-surface-200 rounded-lg hover:border-primary-300 hover:text-primary-600 transition-colors shadow-sm self-start sm:self-auto"
            @click="fetchProfile"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {{ t('mypage.reload') }}
          </button>
        </div>
      </div>

      <!-- Loading Skeleton State -->
      <div v-if="isLoading" class="space-y-6">
        <div class="card p-6 sm:p-8 animate-pulse">
          <div class="flex flex-col sm:flex-row items-center sm:items-start gap-6">
            <div class="w-24 h-24 rounded-2xl bg-surface-200 shrink-0" />
            <div class="flex-1 w-full text-center sm:text-left space-y-3">
              <div class="h-7 bg-surface-200 rounded-lg w-40 mx-auto sm:mx-0" />
              <div class="h-4 bg-surface-200 rounded w-28 mx-auto sm:mx-0" />
              <div class="flex gap-2 justify-center sm:justify-start pt-2">
                <div class="h-6 bg-surface-200 rounded-full w-20" />
                <div class="h-6 bg-surface-200 rounded-full w-24" />
              </div>
            </div>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div class="card p-6 animate-pulse space-y-4">
            <div class="h-5 bg-surface-200 rounded w-32" />
            <div class="space-y-3 pt-2">
              <div class="h-4 bg-surface-200 rounded w-full" />
              <div class="h-4 bg-surface-200 rounded w-full" />
              <div class="h-4 bg-surface-200 rounded w-full" />
            </div>
          </div>
          <div class="card p-6 animate-pulse space-y-4">
            <div class="h-5 bg-surface-200 rounded w-32" />
            <div class="space-y-3 pt-2">
              <div class="h-4 bg-surface-200 rounded w-full" />
              <div class="h-4 bg-surface-200 rounded w-full" />
            </div>
          </div>
        </div>
      </div>

      <!-- Main Profile Content -->
      <div v-else class="space-y-6">
        
        <!-- Profile Banner Card -->
        <div class="card relative overflow-hidden p-6 sm:p-8 bg-gradient-to-br from-white via-white to-primary-50/30 border border-surface-200/80 shadow-md">
          <!-- Background decorative aura -->
          <div class="absolute -top-12 -right-12 w-48 h-48 bg-gradient-to-br from-primary-400/15 to-accent-400/20 rounded-full blur-2xl pointer-events-none" />

          <div class="relative z-10 flex flex-col sm:flex-row items-center sm:items-start gap-6">
            <!-- Avatar with gradient ring -->
            <div class="relative shrink-0">
              <div class="w-24 h-24 rounded-2xl bg-gradient-to-tr from-primary-500 via-primary-600 to-accent-500 p-0.5 shadow-lg shadow-primary-500/20">
                <div class="w-full h-full bg-white rounded-[14px] flex items-center justify-center text-3xl font-extrabold bg-gradient-to-tr from-primary-600 to-accent-500 bg-clip-text text-transparent">
                  {{ (displayUser?.nickname || displayUser?.username || 'U').charAt(0).toUpperCase() }}
                </div>
              </div>
              <span
                class="absolute -bottom-1 -right-1 w-5 h-5 bg-emerald-500 border-2 border-white rounded-full flex items-center justify-center shadow"
                :title="t('mypage.statusActive')"
              >
                <span class="w-1.5 h-1.5 bg-white rounded-full" />
              </span>
            </div>

            <!-- Profile Overview Info -->
            <div class="flex-1 text-center sm:text-left space-y-2">
              <div class="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-3">
                <h2 class="text-2xl font-bold text-surface-900">
                  {{ displayUser?.nickname || displayUser?.username }}
                </h2>
                <div class="flex items-center justify-center sm:justify-start gap-2">
                  <!-- Role Badge -->
                  <span
                    class="px-2.5 py-0.5 text-xs font-semibold rounded-full bg-primary-100 text-primary-700 border border-primary-200/60"
                  >
                    {{ roleLabel(displayUser?.role) }}
                  </span>
                  <!-- Provider Badge -->
                  <span
                    class="px-2.5 py-0.5 text-xs font-medium rounded-full bg-surface-100 text-surface-600 border border-surface-200"
                  >
                    {{ providerLabel(displayUser?.authProvider) }}
                  </span>
                </div>
              </div>

              <p class="text-sm font-mono text-surface-500">
                @{{ displayUser?.username }}
              </p>

              <div class="pt-2 flex flex-wrap items-center justify-center sm:justify-start gap-4 text-xs text-surface-500">
                <span class="inline-flex items-center gap-1.5">
                  <svg class="w-4 h-4 text-surface-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                  {{ displayUser?.email || t('mypage.notSet') }}
                </span>
                <span class="inline-flex items-center gap-1.5">
                  <svg class="w-4 h-4 text-surface-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                  {{ formatDate(displayUser?.createdAt) }}
                </span>
              </div>
            </div>

            <!-- Logout Button -->
            <div class="pt-2 sm:pt-0">
              <button
                type="button"
                class="btn-outline !py-2 !px-4 text-xs sm:text-sm text-surface-600 hover:text-red-600 hover:border-red-300 disabled:opacity-50"
                :disabled="isLoggingOut"
                @click="handleLogout"
              >
                <svg class="w-4 h-4 mr-1.5 text-surface-400 group-hover:text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                </svg>
                {{ t('common.logout') }}
              </button>
            </div>
          </div>
        </div>

        <!-- Detail Cards Grid -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          
          <!-- Detailed Account Info -->
          <div class="card p-6 bg-white border border-surface-200/80 shadow-sm hover:shadow-md transition-shadow">
            <div class="flex items-center gap-2 mb-4 pb-3 border-b border-surface-100">
              <div class="w-8 h-8 rounded-lg bg-primary-50 text-primary-600 flex items-center justify-center">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
              </div>
              <h3 class="font-bold text-lg text-surface-900">
                {{ t('mypage.accountDetails') }}
              </h3>
            </div>

            <dl class="divide-y divide-surface-100 text-sm">
              <div class="py-3 flex items-center justify-between">
                <dt class="text-surface-500 font-medium">{{ t('mypage.userId') }}</dt>
                <dd class="font-mono text-surface-800 font-semibold">#{{ displayUser?.id }}</dd>
              </div>
              <div class="py-3 flex items-center justify-between">
                <dt class="text-surface-500 font-medium">{{ t('mypage.username') }}</dt>
                <dd class="font-mono text-surface-800">{{ displayUser?.username }}</dd>
              </div>
              <div class="py-3 flex items-center justify-between">
                <dt class="text-surface-500 font-medium">{{ t('mypage.nickname') }}</dt>
                <dd class="text-surface-800 font-medium">{{ displayUser?.nickname }}</dd>
              </div>
              <div class="py-3 flex items-center justify-between">
                <dt class="text-surface-500 font-medium">{{ t('mypage.email') }}</dt>
                <dd class="text-surface-800">
                  {{ displayUser?.email || t('mypage.notSet') }}
                </dd>
              </div>
              <div class="py-3 flex items-center justify-between">
                <dt class="text-surface-500 font-medium">{{ t('mypage.authProvider') }}</dt>
                <dd class="text-surface-800 font-medium">
                  {{ providerLabel(displayUser?.authProvider) }}
                </dd>
              </div>
              <div class="py-3 flex items-center justify-between">
                <dt class="text-surface-500 font-medium">{{ t('mypage.createdAt') }}</dt>
                <dd class="text-surface-700 text-xs sm:text-sm">
                  {{ formatDate(displayUser?.createdAt) }}
                </dd>
              </div>
            </dl>
          </div>

          <!-- Quick Action Navigation -->
          <div class="card p-6 bg-white border border-surface-200/80 shadow-sm hover:shadow-md transition-shadow">
            <div class="flex items-center gap-2 mb-4 pb-3 border-b border-surface-100">
              <div class="w-8 h-8 rounded-lg bg-accent-50 text-accent-600 flex items-center justify-center">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
              </div>
              <h3 class="font-bold text-lg text-surface-900">
                {{ t('mypage.quickActions') }}
              </h3>
            </div>

            <div class="space-y-3">
              <!-- Orders Shortcut -->
              <button
                type="button"
                class="w-full text-left p-3.5 rounded-xl border border-surface-100 hover:border-primary-200 hover:bg-primary-50/30 transition-all flex items-center justify-between group"
                @click="showOrderModal = true"
              >
                <div class="flex items-center gap-3">
                  <div class="w-10 h-10 rounded-lg bg-orange-100/70 text-orange-600 flex items-center justify-center text-lg">
                    📦
                  </div>
                  <div>
                    <h4 class="text-sm font-semibold text-surface-800 group-hover:text-primary-600 transition-colors">
                      {{ t('mypage.orders') }}
                    </h4>
                    <p class="text-xs text-surface-500">
                      {{ t('mypage.ordersDesc') }}
                    </p>
                  </div>
                </div>
                <span class="text-xs px-2 py-0.5 rounded bg-surface-100 text-surface-500 font-medium group-hover:bg-primary-100 group-hover:text-primary-600 transition-colors">
                  {{ t('mypage.comingSoon') }}
                </span>
              </button>

              <!-- Explore Restaurants Shortcut -->
              <NuxtLink
                to="/"
                class="p-3.5 rounded-xl border border-surface-100 hover:border-primary-200 hover:bg-primary-50/30 transition-all flex items-center justify-between group"
              >
                <div class="flex items-center gap-3">
                  <div class="w-10 h-10 rounded-lg bg-rose-100/70 text-rose-600 flex items-center justify-center text-lg">
                    🍜
                  </div>
                  <div>
                    <h4 class="text-sm font-semibold text-surface-800 group-hover:text-primary-600 transition-colors">
                      {{ t('mypage.explore') }}
                    </h4>
                    <p class="text-xs text-surface-500">
                      {{ t('mypage.exploreDesc') }}
                    </p>
                  </div>
                </div>
                <svg class="w-4 h-4 text-surface-400 group-hover:text-primary-600 group-hover:translate-x-1 transition-all" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                </svg>
              </NuxtLink>

              <!-- Account Security / Password -->
              <button
                type="button"
                class="w-full text-left p-3.5 rounded-xl border border-surface-100 hover:border-primary-200 hover:bg-primary-50/30 transition-all flex items-center justify-between group"
                @click="showSecurityModal = true"
              >
                <div class="flex items-center gap-3">
                  <div class="w-10 h-10 rounded-lg bg-blue-100/70 text-blue-600 flex items-center justify-center text-lg">
                    🛡️
                  </div>
                  <div>
                    <h4 class="text-sm font-semibold text-surface-800 group-hover:text-primary-600 transition-colors">
                      {{ t('mypage.security') }}
                    </h4>
                    <p class="text-xs text-surface-500">
                      {{ t('mypage.securityDesc') }}
                    </p>
                  </div>
                </div>
                <span class="text-xs px-2 py-0.5 rounded bg-surface-100 text-surface-500 font-medium group-hover:bg-primary-100 group-hover:text-primary-600 transition-colors">
                  {{ t('mypage.comingSoon') }}
                </span>
              </button>
            </div>
          </div>
        </div>

      </div>

      <!-- Simple Info Modal for Order Service / Security (Coming Soon) -->
      <div
        v-if="showOrderModal || showSecurityModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-surface-900/40 backdrop-blur-sm animate-fadeIn"
      >
        <div class="card p-6 max-w-sm w-full space-y-4 shadow-xl text-center">
          <div class="w-12 h-12 rounded-full bg-primary-100 text-primary-600 flex items-center justify-center mx-auto text-xl">
            {{ showOrderModal ? '📦' : '🛡️' }}
          </div>
          <div>
            <h3 class="font-bold text-lg text-surface-900">
              {{ showOrderModal ? t('mypage.orders') : t('mypage.security') }}
            </h3>
            <p class="text-sm text-surface-500 mt-1">
              {{ showOrderModal ? t('mypage.ordersDesc') : t('mypage.securityDesc') }}
            </p>
            <p class="text-xs text-primary-600 font-medium mt-2 bg-primary-50 py-1 px-3 rounded-full inline-block">
              {{ t('mypage.comingSoon') }}
            </p>
          </div>
          <button
            type="button"
            class="btn-primary !w-full !py-2 text-sm !rounded-xl"
            @click="showOrderModal = false; showSecurityModal = false"
          >
            확인
          </button>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped>
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fadeInUp {
  animation: fadeInUp 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.animate-fadeIn {
  animation: fadeIn 0.2s ease-out forwards;
}
</style>
