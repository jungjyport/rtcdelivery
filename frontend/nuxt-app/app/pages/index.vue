<template>
  <div class="min-h-screen bg-surface-50">
    <!-- Navigation -->
    <nav class="fixed top-0 left-0 right-0 z-50 glass border-b border-white/10">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between h-16">
          <!-- Logo -->
          <div class="flex items-center gap-2">
            <div class="w-9 h-9 bg-gradient-to-br from-primary-500 to-accent-500 rounded-xl flex items-center justify-center shadow-lg shadow-primary-500/20">
              <span class="text-white text-lg">🔥</span>
            </div>
            <span class="text-xl font-bold bg-gradient-to-r from-primary-600 to-accent-500 bg-clip-text text-transparent">
              RTC Delivery
            </span>
          </div>

          <!-- Desktop Nav -->
          <div class="hidden md:flex items-center gap-8">
            <a href="#" class="text-surface-600 hover:text-primary-500 font-medium transition-colors duration-200">{{ $t('nav.home') }}</a>
            <a href="#" class="text-surface-600 hover:text-primary-500 font-medium transition-colors duration-200">{{ $t('nav.findRestaurants') }}</a>
            <a href="#" class="text-surface-600 hover:text-primary-500 font-medium transition-colors duration-200">{{ $t('nav.orderHistory') }}</a>
          </div>

          <!-- Auth Buttons + Locale Switcher -->
          <div class="flex items-center gap-3">
            <!-- Locale Switcher -->
            <div class="relative" ref="localeSwitcherRef">
              <button
                class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-surface-600 hover:text-primary-500 hover:bg-surface-100 transition-all duration-200"
                @click="isLocaleMenuOpen = !isLocaleMenuOpen"
              >
                <span>{{ currentLocaleFlag }}</span>
                <svg class="w-3.5 h-3.5 transition-transform duration-200" :class="{ 'rotate-180': isLocaleMenuOpen }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                </svg>
              </button>
              <Transition
                enter-active-class="transition ease-out duration-100"
                enter-from-class="transform opacity-0 scale-95"
                enter-to-class="transform opacity-100 scale-100"
                leave-active-class="transition ease-in duration-75"
                leave-from-class="transform opacity-100 scale-100"
                leave-to-class="transform opacity-0 scale-95"
              >
                <div
                  v-if="isLocaleMenuOpen"
                  class="absolute right-0 mt-2 w-36 bg-white rounded-xl shadow-lg border border-surface-100 py-1 z-50"
                >
                  <button
                    v-for="loc in availableLocales"
                    :key="loc.code"
                    class="w-full flex items-center gap-2.5 px-4 py-2.5 text-sm hover:bg-surface-50 transition-colors duration-150"
                    :class="{ 'text-primary-600 font-semibold bg-primary-50/50': locale === loc.code, 'text-surface-700': locale !== loc.code }"
                    @click="switchLocale(loc.code)"
                  >
                    <span>{{ loc.flag }}</span>
                    <span>{{ loc.name }}</span>
                  </button>
                </div>
              </Transition>
            </div>

            <button class="hidden sm:inline-flex text-surface-600 hover:text-primary-500 font-medium transition-colors duration-200">
              {{ $t('common.login') }}
            </button>
            <button class="btn-primary text-sm !px-4 !py-2 !rounded-lg">
              {{ $t('common.signup') }}
            </button>
          </div>
        </div>
      </div>
    </nav>

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

    <!-- Footer -->
    <footer class="bg-surface-900 text-surface-400 py-12">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="grid md:grid-cols-4 gap-8 mb-8">
          <div>
            <div class="flex items-center gap-2 mb-4">
              <div class="w-8 h-8 bg-gradient-to-br from-primary-500 to-accent-500 rounded-lg flex items-center justify-center">
                <span class="text-white text-sm">🔥</span>
              </div>
              <span class="text-lg font-bold text-white">RTC Delivery</span>
            </div>
            <p class="text-sm leading-relaxed">
              <span v-for="(part, i) in footerDescParts" :key="i">
                <br v-if="i > 0" />{{ part }}
              </span>
            </p>
          </div>
          <div>
            <h4 class="font-semibold text-white mb-3">{{ $t('footer.service') }}</h4>
            <ul class="space-y-2 text-sm">
              <li><a href="#" class="hover:text-white transition-colors">{{ $t('footer.findRestaurants') }}</a></li>
              <li><a href="#" class="hover:text-white transition-colors">{{ $t('footer.order') }}</a></li>
              <li><a href="#" class="hover:text-white transition-colors">{{ $t('footer.liveTracking') }}</a></li>
            </ul>
          </div>
          <div>
            <h4 class="font-semibold text-white mb-3">{{ $t('footer.support') }}</h4>
            <ul class="space-y-2 text-sm">
              <li><a href="#" class="hover:text-white transition-colors">{{ $t('footer.faq') }}</a></li>
              <li><a href="#" class="hover:text-white transition-colors">{{ $t('footer.contactCenter') }}</a></li>
              <li><a href="#" class="hover:text-white transition-colors">{{ $t('footer.partnership') }}</a></li>
            </ul>
          </div>
          <div>
            <h4 class="font-semibold text-white mb-3">{{ $t('footer.legal') }}</h4>
            <ul class="space-y-2 text-sm">
              <li><a href="#" class="hover:text-white transition-colors">{{ $t('footer.terms') }}</a></li>
              <li><a href="#" class="hover:text-white transition-colors">{{ $t('footer.privacy') }}</a></li>
              <li><a href="#" class="hover:text-white transition-colors">{{ $t('footer.businessInfo') }}</a></li>
            </ul>
          </div>
        </div>
        <div class="border-t border-surface-800 pt-8 text-center text-sm">
          <p>{{ $t('footer.copyright') }}</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
const { t, locale } = useI18n()

// SEO Meta (i18n 반응형)
useHead({
  title: () => t('seo.title'),
  meta: [
    { name: 'description', content: () => t('seo.description') },
  ],
})

// Locale Switcher
const isLocaleMenuOpen = ref(false)
const localeSwitcherRef = ref<HTMLElement | null>(null)

const availableLocales = [
  { code: 'ko', name: '한국어', flag: '🇰🇷' },
  { code: 'ja', name: '日本語', flag: '🇯🇵' },
] as const

type LocaleCode = typeof availableLocales[number]['code']

const currentLocaleFlag = computed(() => {
  return availableLocales.find(l => l.code === locale.value)?.flag ?? '🌐'
})

const { setLocale } = useI18n()

function switchLocale(code: LocaleCode) {
  setLocale(code)
  isLocaleMenuOpen.value = false
}

// Close locale menu on click outside
function handleClickOutside(event: MouseEvent) {
  if (localeSwitcherRef.value && !localeSwitcherRef.value.contains(event.target as Node)) {
    isLocaleMenuOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

// Description text with {br} split
const descriptionParts = computed(() => t('hero.description').split('{br}'))
const footerDescParts = computed(() => t('footer.description').split('{br}'))

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
