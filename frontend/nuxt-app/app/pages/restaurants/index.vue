<script setup lang="ts">
import { computed } from 'vue'
import CategoryBar from '~/components/food/CategoryBar.vue'
import SearchBar from '~/components/common/SearchBar.vue'
import { useCatalog } from '~/composables/useCatalog'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { useCategoriesFetch, useRestaurantsFetch } = useCatalog()

useHead({
  title: () => `${t('catalog.title')} - RTC Delivery`,
  meta: [
    { name: 'description', content: () => t('seo.description') },
  ],
})

// 카테고리 목록 조회
const { data: categories } = await useCategoriesFetch()

// 현재 선택된 카테고리 ID
const selectedCategoryId = computed(() => {
  const c = route.query.categoryId
  return c ? Number(c) : null
})

// 현재 정렬 기준
const currentSort = computed(() => (route.query.sort as string) || 'id,desc')

// 현재 페이지 (0-indexed)
const currentPage = computed(() => {
  const p = route.query.page
  return p ? Number(p) : 0
})

// 이름 검색어 (백엔드 GET /restaurants 의 keyword 파라미터)
const currentKeyword = computed(() => (route.query.keyword as string) || '')

// API 쿼리 파라미터 반응형 객체
const queryParams = computed(() => ({
  categoryId: selectedCategoryId.value || undefined,
  sort: currentSort.value,
  page: currentPage.value,
  size: 9,
  keyword: currentKeyword.value || undefined,
}))

// 음식점 목록 조회 (SSR 프리페치)
const { data: pageData, pending } = await useRestaurantsFetch(queryParams)

const restaurants = computed(() => pageData.value?.content || [])
const totalPages = computed(() => pageData.value?.totalPages || 0)
const totalElements = computed(() => pageData.value?.totalElements || 0)

function onSelectCategory(id: number | null) {
  router.push({
    query: {
      ...route.query,
      categoryId: id ? String(id) : undefined,
      page: undefined, // 카테고리 변경 시 첫 페이지로
    },
  })
}

function onSortChange(event: Event) {
  const val = (event.target as HTMLSelectElement).value
  router.push({
    query: {
      ...route.query,
      sort: val,
      page: undefined,
    },
  })
}

function onSearch(keyword: string) {
  router.push({
    query: {
      ...route.query,
      keyword: keyword || undefined,
      page: undefined,
    },
  })
}

function goToPage(p: number) {
  router.push({
    query: {
      ...route.query,
      page: p > 0 ? String(p) : undefined,
    },
  })
}
</script>

<template>
  <div class="min-h-screen bg-surface-50 pt-20 pb-16">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <!-- 페이지 헤더 -->
      <div class="mb-8">
        <div class="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4 mb-4">
          <div>
            <h1 class="text-3xl sm:text-4xl font-black text-surface-900 mb-1">
              {{ t('catalog.title') }}
            </h1>
            <p class="text-surface-500">
              {{ t('category.subtitle') }}
            </p>
          </div>
          <!-- 인라인 서치바 -->
          <div class="w-full sm:w-80">
            <SearchBar
              :placeholder="$t('hero.searchPlaceholder')"
              :model-value="currentKeyword"
              size="sm"
              @search="onSearch"
            />
          </div>
        </div>
      </div>

      <!-- 카테고리 칩 바 -->
      <div class="mb-8">
        <CategoryBar
          :categories="categories || []"
          :selected-id="selectedCategoryId"
          @select="onSelectCategory"
        />
      </div>

      <!-- 서브 툴바: 개수 및 정렬 필터 -->
      <div class="flex items-center justify-between mb-6 pb-4 border-b border-surface-200/60">
        <div class="text-sm font-medium text-surface-600">
          <span class="text-primary-600 font-bold">{{ totalElements }}</span>개의 맛집
        </div>

        <div class="flex items-center gap-2">
          <select
            :value="currentSort"
            class="text-sm bg-white border border-surface-200 rounded-xl px-3 py-2 text-surface-700 outline-none focus:border-primary-500 shadow-sm"
            @change="onSortChange"
          >
            <option value="id,desc">{{ t('catalog.sort.latest') }}</option>
            <option value="deliveryFee,asc">{{ t('catalog.sort.deliveryFee') }}</option>
            <option value="minOrderAmount,asc">{{ t('catalog.sort.minOrder') }}</option>
          </select>
        </div>
      </div>

      <!-- 로딩 스켈레톤 -->
      <div v-if="pending" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <div
          v-for="i in 6"
          :key="i"
          class="card overflow-hidden animate-pulse"
        >
          <div class="h-48 bg-surface-200"></div>
          <div class="p-5 space-y-3">
            <div class="h-5 bg-surface-200 rounded w-2/3"></div>
            <div class="h-4 bg-surface-200 rounded w-full"></div>
            <div class="h-4 bg-surface-200 rounded w-1/2"></div>
          </div>
        </div>
      </div>

      <!-- 빈 결과 -->
      <div
        v-else-if="restaurants.length === 0"
        class="card p-12 text-center my-12"
      >
        <div class="text-5xl mb-4">🔍</div>
        <h3 class="text-lg font-bold text-surface-800 mb-2">
          {{ t('catalog.noRestaurants') }}
        </h3>
        <p class="text-surface-500 text-sm mb-6">
          다른 카테고리를 선택하거나 검색어를 변경해보세요.
        </p>
        <button
          type="button"
          class="btn-primary !px-6 !py-2.5 !rounded-xl text-sm"
          @click="onSelectCategory(null)"
        >
          {{ t('catalog.allCategories') }} 맛집 보기
        </button>
      </div>

      <!-- 음식점 카드 그리드 -->
      <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <NuxtLink
          v-for="restaurant in restaurants"
          :key="restaurant.id"
          :to="`/restaurants/${restaurant.id}`"
          class="group card overflow-hidden hover:-translate-y-1 hover:shadow-xl transition-all duration-300 flex flex-col bg-white"
        >
          <!-- 썸네일 이미지 -->
          <div class="relative h-48 w-full overflow-hidden bg-surface-100">
            <img
              v-if="restaurant.imageUrl"
              :src="restaurant.imageUrl"
              :alt="restaurant.name"
              class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
              loading="lazy"
            />
            <div
              v-else
              class="w-full h-full flex items-center justify-center bg-gradient-to-br from-primary-50 to-accent-50 text-surface-400 text-4xl"
            >
              🍽️
            </div>

            <!-- 카테고리 뱃지 -->
            <div class="absolute top-3 left-3 px-3 py-1 rounded-full bg-surface-900/70 backdrop-blur-md text-white text-xs font-semibold">
              {{ t(`category.${restaurant.categoryCode}`) }}
            </div>
          </div>

          <!-- 카드 본문 -->
          <div class="p-5 flex-1 flex flex-col justify-between">
            <div>
              <h2 class="text-lg font-bold text-surface-900 group-hover:text-primary-600 transition-colors duration-200 line-clamp-1 mb-1">
                {{ restaurant.name }}
              </h2>
              <p class="text-sm text-surface-500 line-clamp-2 mb-4">
                {{ restaurant.description || restaurant.address }}
              </p>
            </div>

            <div class="pt-3 border-t border-surface-100 flex items-center justify-between text-xs text-surface-600 font-medium">
              <div class="flex items-center gap-1.5">
                <span>🛵</span>
                <span>{{ t('catalog.deliveryFee') }}:</span>
                <span class="font-bold text-surface-900">
                  {{ restaurant.deliveryFee > 0 ? `${restaurant.deliveryFee.toLocaleString()}${t('catalog.currency')}` : t('catalog.freeDelivery') }}
                </span>
              </div>
              <div class="flex items-center gap-1">
                <span>{{ t('catalog.minOrderAmount') }}:</span>
                <span class="font-bold text-surface-900">
                  {{ restaurant.minOrderAmount.toLocaleString() }}{{ t('catalog.currency') }}
                </span>
              </div>
            </div>
          </div>
        </NuxtLink>
      </div>

      <!-- 페이지네이션 -->
      <div
        v-if="totalPages > 1"
        class="flex items-center justify-center gap-2 mt-12"
      >
        <button
          type="button"
          class="px-4 py-2 rounded-xl text-sm font-medium border border-surface-200 bg-white hover:bg-surface-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          :disabled="currentPage === 0"
          @click="goToPage(currentPage - 1)"
        >
          ← 이전
        </button>

        <button
          v-for="p in totalPages"
          :key="p"
          type="button"
          class="w-10 h-10 rounded-xl text-sm font-semibold transition-all"
          :class="currentPage === p - 1
            ? 'bg-primary-500 text-white shadow-md shadow-primary-500/25'
            : 'bg-white text-surface-700 hover:bg-surface-100 border border-surface-200'"
          @click="goToPage(p - 1)"
        >
          {{ p }}
        </button>

        <button
          type="button"
          class="px-4 py-2 rounded-xl text-sm font-medium border border-surface-200 bg-white hover:bg-surface-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          :disabled="currentPage >= totalPages - 1"
          @click="goToPage(currentPage + 1)"
        >
          다음 →
        </button>
      </div>
    </div>
  </div>
</template>
