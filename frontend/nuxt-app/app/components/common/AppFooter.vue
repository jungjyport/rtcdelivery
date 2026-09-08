<script setup lang="ts">
const { t } = useI18n()

const footerDescParts = computed(() => t('footer.description').split('{br}'))
</script>

<template>
  <footer class="bg-surface-900 text-surface-400 py-12">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="grid md:grid-cols-4 gap-8 mb-8">
        <div>
          <div class="flex items-center gap-2 mb-4">
            <div class="w-8 h-8 bg-gradient-to-br from-primary-500 to-accent-500 rounded-lg flex items-center justify-center">
              <span class="text-white text-sm" aria-hidden="true">🔥</span>
            </div>
            <span class="text-lg font-bold text-white">RTC Delivery</span>
          </div>
          <p class="text-sm leading-relaxed">
            <span v-for="(part, i) in footerDescParts" :key="i">
              <br v-if="i > 0">{{ part }}
            </span>
          </p>
        </div>
        <div>
          <h4 class="font-semibold text-white mb-3">{{ t('footer.service') }}</h4>
          <ul class="space-y-2 text-sm">
            <li><a href="#" class="hover:text-white transition-colors">{{ t('footer.findRestaurants') }}</a></li>
            <li><a href="#" class="hover:text-white transition-colors">{{ t('footer.order') }}</a></li>
            <li><a href="#" class="hover:text-white transition-colors">{{ t('footer.liveTracking') }}</a></li>
          </ul>
        </div>
        <div>
          <h4 class="font-semibold text-white mb-3">{{ t('footer.support') }}</h4>
          <ul class="space-y-2 text-sm">
            <li><a href="#" class="hover:text-white transition-colors">{{ t('footer.faq') }}</a></li>
            <li><a href="#" class="hover:text-white transition-colors">{{ t('footer.contactCenter') }}</a></li>
            <li><a href="#" class="hover:text-white transition-colors">{{ t('footer.partnership') }}</a></li>
          </ul>
        </div>
        <div>
          <h4 class="font-semibold text-white mb-3">{{ t('footer.legal') }}</h4>
          <ul class="space-y-2 text-sm">
            <li><a href="#" class="hover:text-white transition-colors">{{ t('footer.terms') }}</a></li>
            <li><a href="#" class="hover:text-white transition-colors">{{ t('footer.privacy') }}</a></li>
            <li><a href="#" class="hover:text-white transition-colors">{{ t('footer.businessInfo') }}</a></li>
          </ul>
        </div>
      </div>
      <div class="border-t border-surface-800 pt-8 text-center text-sm">
        <p>{{ t('footer.copyright') }}</p>
      </div>
    </div>
  </footer>
</template>
