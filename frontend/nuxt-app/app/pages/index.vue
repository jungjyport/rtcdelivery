<template>
  <div>
    <!-- Hero Section -->
    <section class="relative pt-16 overflow-hidden">
      <!-- Background Gradient -->
      <div class="absolute inset-0 bg-gradient-to-br from-primary-500/5 via-transparent to-accent-500/5"></div>
      <div class="absolute top-20 left-10 w-72 h-72 bg-primary-400/10 rounded-full blur-3xl animate-pulse"></div>
      <div class="absolute top-40 right-10 w-96 h-96 bg-accent-400/10 rounded-full blur-3xl animate-pulse" style="animation-delay: 1s;"></div>

      <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20 md:py-32">
        <div class="text-center max-w-4xl mx-auto">
          <!-- Badge -->
          <div class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-primary-50 border border-primary-100 mb-8 animate-fadeInUp">
            <span class="w-2 h-2 rounded-full bg-primary-500 animate-ping-slow"></span>
            <span class="text-sm font-medium text-primary-700">{{ $t('hero.badge') }}</span>
          </div>

          <!-- Heading -->
          <h1 class="text-4xl sm:text-5xl md:text-6xl lg:text-7xl font-black leading-tight mb-6 animate-fadeInUp" style="animation-delay: 0.1s;">
            <span class="text-surface-900">{{ $t('hero.heading1') }}</span>
            <br />
            <span class="bg-gradient-to-r from-primary-500 via-primary-600 to-accent-500 bg-clip-text text-transparent">
              {{ $t('hero.heading2') }}
            </span>
          </h1>

          <!-- Description -->
          <p class="text-lg sm:text-xl text-surface-500 max-w-2xl mx-auto mb-10 animate-fadeInUp" style="animation-delay: 0.2s;">
            <span v-for="(part, i) in descriptionParts" :key="i">
              <br v-if="i > 0" class="hidden sm:inline" />{{ part }}
            </span>
          </p>

          <!-- Search Bar -->
          <div class="max-w-xl mx-auto animate-fadeInUp" style="animation-delay: 0.3s;">
            <div class="flex items-center bg-white rounded-2xl shadow-xl shadow-surface-900/5 border border-surface-100 p-2 hover:shadow-2xl transition-shadow duration-300">
              <div class="flex items-center gap-2 px-4 flex-1">
                <svg class="w-5 h-5 text-surface-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                <input
                  type="text"
                  :placeholder="$t('hero.searchPlaceholder')"
                  class="w-full py-2 text-surface-900 placeholder-surface-400 outline-none bg-transparent"
                />
              </div>
              <button class="btn-primary !rounded-xl !py-2.5 !px-6 shrink-0">
                {{ $t('common.search') }}
              </button>
            </div>
          </div>

          <!-- Stats -->
          <div class="flex items-center justify-center gap-8 sm:gap-12 mt-12 animate-fadeInUp" style="animation-delay: 0.4s;">
            <div class="text-center">
              <p class="text-2xl sm:text-3xl font-bold text-surface-900">3,200+</p>
              <p class="text-sm text-surface-500 mt-1">{{ $t('hero.stats.restaurants') }}</p>
            </div>
            <div class="w-px h-10 bg-surface-200"></div>
            <div class="text-center">
              <p class="text-2xl sm:text-3xl font-bold text-surface-900">{{ $t('hero.stats.avgDeliveryValue') }}</p>
              <p class="text-sm text-surface-500 mt-1">{{ $t('hero.stats.avgDelivery') }}</p>
            </div>
            <div class="w-px h-10 bg-surface-200"></div>
            <div class="text-center">
              <p class="text-2xl sm:text-3xl font-bold text-surface-900">4.8<span class="text-accent-500">★</span></p>
              <p class="text-sm text-surface-500 mt-1">{{ $t('hero.stats.satisfaction') }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Categories Section -->
    <section class="py-16 md:py-24">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-12">
          <h2 class="text-3xl md:text-4xl font-bold text-surface-900 mb-4">{{ $t('category.title') }}</h2>
          <p class="text-surface-500 text-lg">{{ $t('category.subtitle') }}</p>
        </div>

        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
          <div
            v-for="category in categories"
            :key="category.key"
            class="group cursor-pointer"
          >
            <div class="card p-6 text-center hover:border-primary-200 hover:-translate-y-1 transition-all duration-300">
              <div class="text-4xl mb-3 group-hover:scale-110 transition-transform duration-300">
                {{ category.icon }}
              </div>
              <p class="font-semibold text-surface-700 group-hover:text-primary-600 transition-colors duration-200">
                {{ $t(`category.${category.key}`) }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="py-16 md:py-24 bg-gradient-to-b from-surface-50 to-white">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-16">
          <h2 class="text-3xl md:text-4xl font-bold text-surface-900 mb-4">{{ $t('feature.title') }}</h2>
          <p class="text-surface-500 text-lg">{{ $t('feature.subtitle') }}</p>
        </div>

        <div class="grid md:grid-cols-3 gap-8">
          <div
            v-for="feature in features"
            :key="feature.key"
            class="card p-8 text-center hover:-translate-y-2 transition-all duration-300"
          >
            <div class="w-16 h-16 mx-auto mb-6 rounded-2xl flex items-center justify-center text-3xl"
                 :class="feature.bgClass">
              {{ feature.icon }}
            </div>
            <h3 class="text-xl font-bold text-surface-900 mb-3">{{ $t(`feature.${feature.key}.title`) }}</h3>
            <p class="text-surface-500 leading-relaxed">{{ $t(`feature.${feature.key}.description`) }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="py-16 md:py-24">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="relative overflow-hidden rounded-3xl bg-gradient-to-br from-primary-500 via-primary-600 to-accent-500 p-12 md:p-16 text-center">
          <!-- Decorative -->
          <div class="absolute top-0 left-0 w-64 h-64 bg-white/10 rounded-full -translate-x-1/2 -translate-y-1/2"></div>
          <div class="absolute bottom-0 right-0 w-96 h-96 bg-white/5 rounded-full translate-x-1/3 translate-y-1/3"></div>

          <div class="relative">
            <h2 class="text-3xl md:text-4xl font-bold text-white mb-4">{{ $t('cta.title') }}</h2>
            <p class="text-white/80 text-lg mb-8 max-w-lg mx-auto">
              {{ $t('cta.description') }}
            </p>
            <div class="flex items-center justify-center gap-4">
              <button class="inline-flex items-center px-8 py-3.5 bg-white text-primary-600 font-bold rounded-xl hover:bg-surface-50 transition-all duration-200 shadow-lg shadow-black/10">
                {{ $t('cta.orderNow') }}
              </button>
              <button class="inline-flex items-center px-8 py-3.5 border-2 border-white/30 text-white font-semibold rounded-xl hover:bg-white/10 transition-all duration-200">
                {{ $t('cta.appDownload') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const { t } = useI18n()

useHead({
  title: () => t('seo.title'),
  meta: [
    { name: 'description', content: () => t('seo.description') },
  ],
})

const descriptionParts = computed(() => t('hero.description').split('{br}'))

// Categories (icon + key for i18n lookup)
const categories = [
  { key: 'korean', icon: '🍚' },
  { key: 'chinese', icon: '🥡' },
  { key: 'japanese', icon: '🍣' },
  { key: 'western', icon: '🍝' },
  { key: 'chicken', icon: '🍗' },
  { key: 'pizza', icon: '🍕' },
  { key: 'burger', icon: '🍔' },
  { key: 'snack', icon: '🍜' },
  { key: 'cafe', icon: '☕' },
  { key: 'dessert', icon: '🍰' },
  { key: 'lateNight', icon: '🌙' },
  { key: 'healthy', icon: '🥗' },
]

// Features (icon + key for i18n lookup)
const features = [
  {
    key: 'realtime',
    icon: '⚡',
    bgClass: 'bg-primary-50 text-primary-500',
  },
  {
    key: 'fast',
    icon: '🚀',
    bgClass: 'bg-accent-50 text-accent-500',
  },
  {
    key: 'secure',
    icon: '🛡️',
    bgClass: 'bg-emerald-50 text-emerald-500',
  },
]



</script>

<style scoped>
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fadeInUp {
  animation: fadeInUp 0.6s ease-out forwards;
  opacity: 0;
}

@keyframes ping-slow {
  75%, 100% {
    transform: scale(2);
    opacity: 0;
  }
}

.animate-ping-slow {
  animation: ping-slow 2s cubic-bezier(0, 0, 0.2, 1) infinite;
}
</style>
