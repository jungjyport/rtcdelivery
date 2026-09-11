<script setup lang="ts">
import type { CategoryResponse } from '~/types/catalog'

const props = defineProps<{
  categories: CategoryResponse[]
  selectedId?: number | null
}>()

const emit = defineEmits<{
  (e: 'select', id: number | null): void
}>()

const { t } = useI18n()

// Category emoji icons by code
const categoryIcons: Record<string, string> = {
  korean: '🍚',
  chinese: '🥡',
  japanese: '🍣',
  western: '🍝',
  chicken: '🍗',
  pizza: '🍕',
  burger: '🍔',
  snack: '🍜',
  cafe: '☕',
  dessert: '🍰',
  lateNight: '🌙',
  healthy: '🥗',
}

function getIcon(code: string): string {
  return categoryIcons[code] || '🍽️'
}
</script>

<template>
  <div class="w-full overflow-x-auto scrollbar-none py-2">
    <div class="flex items-center gap-2 sm:gap-3 min-w-max px-1">
      <!-- 전체 칩 -->
      <button
        type="button"
        class="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-semibold transition-all duration-200"
        :class="selectedId == null
          ? 'bg-primary-500 text-white shadow-md shadow-primary-500/25 scale-105'
          : 'bg-white text-surface-600 hover:bg-surface-100 border border-surface-200'"
        @click="emit('select', null)"
      >
        <span>🔥</span>
        <span>{{ t('catalog.allCategories') }}</span>
      </button>

      <!-- 개별 카테고리 칩 -->
      <button
        v-for="cat in categories"
        :key="cat.id"
        type="button"
        class="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-semibold transition-all duration-200"
        :class="selectedId === cat.id
          ? 'bg-primary-500 text-white shadow-md shadow-primary-500/25 scale-105'
          : 'bg-white text-surface-600 hover:bg-surface-100 border border-surface-200'"
        @click="emit('select', cat.id)"
      >
        <span>{{ getIcon(cat.code) }}</span>
        <span>{{ t(`category.${cat.code}`) }}</span>
      </button>
    </div>
  </div>
</template>
