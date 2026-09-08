<script setup lang="ts">
const { locale, setLocale } = useI18n()

const isOpen = ref(false)
const rootRef = ref<HTMLElement | null>(null)

const availableLocales = [
  { code: 'ko', name: '한국어', flag: '🇰🇷' },
  { code: 'ja', name: '日本語', flag: '🇯🇵' },
] as const

type LocaleCode = typeof availableLocales[number]['code']

const currentLocaleFlag = computed(() => {
  return availableLocales.find(l => l.code === locale.value)?.flag ?? '🌐'
})

function switchLocale(code: LocaleCode) {
  setLocale(code)
  isOpen.value = false
}

function handleClickOutside(event: MouseEvent) {
  if (rootRef.value && !rootRef.value.contains(event.target as Node)) {
    isOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div ref="rootRef" class="relative">
    <button
      type="button"
      class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-surface-600 hover:text-primary-500 hover:bg-surface-100 transition-all duration-200"
      :aria-expanded="isOpen"
      aria-haspopup="listbox"
      @click="isOpen = !isOpen"
    >
      <span>{{ currentLocaleFlag }}</span>
      <svg
        class="w-3.5 h-3.5 transition-transform duration-200"
        :class="{ 'rotate-180': isOpen }"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
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
        v-if="isOpen"
        class="absolute right-0 mt-2 w-36 bg-white rounded-xl shadow-lg border border-surface-100 py-1 z-50"
        role="listbox"
      >
        <button
          v-for="loc in availableLocales"
          :key="loc.code"
          type="button"
          role="option"
          class="w-full flex items-center gap-2.5 px-4 py-2.5 text-sm hover:bg-surface-50 transition-colors duration-150"
          :class="{ 'text-primary-600 font-semibold bg-primary-50/50': locale === loc.code, 'text-surface-700': locale !== loc.code }"
          :aria-selected="locale === loc.code"
          @click="switchLocale(loc.code)"
        >
          <span>{{ loc.flag }}</span>
          <span>{{ loc.name }}</span>
        </button>
      </div>
    </Transition>
  </div>
</template>
