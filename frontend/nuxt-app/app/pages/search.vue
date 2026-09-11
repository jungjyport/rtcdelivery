<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { RestaurantResponse, FoodResponse } from '~/types/catalog'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { $api } = useNuxtApp()

const searchQuery = computed(() => (route.query.q as string) || '')
const inputQuery = ref(searchQuery.value)
const activeTab = ref<'restaurants' | 'foods'>('restaurants')

const isLoading = ref(false)
const restaurantResults = ref<RestaurantResponse[]>([])
const foodResults = ref<FoodResponse[]>([])

useHead({
  title: () => `${t('catalog.searchTitle')} - RTC Delivery`,
})

async function fetchResults(q: string) {
  if (!q.trim()) {
    restaurantResults.value = []
    foodResults.value = []
    return
  }

  isLoading.value = true
  try {
    const [restaurantRes, foodRes] = await Promise.all([
      $api.get<{ content: RestaurantResponse[] }>('/restaurants', {
        query: { keyword: q.trim(), size: 30 },
      }),
      $api.get<{ content: FoodResponse[] }>('/foods', {
        query: { keyword: q.trim(), size: 30 },
      }),
    ])

    restaurantResults.value = restaurantRes.content || []
    foodResults.value = foodRes.content || []
  } catch (e) {
    restaurantResults.value = []
    foodResults.value = []
  } finally {
    isLoading.value = false
  }
}

// 쿼리 변경 시 자동 재검색
watch(searchQuery, (newQ) => {
  inputQuery.value = newQ
  fetchResults(newQ)
}, { immediate: true })

function onSearchSubmit() {
  if (!inputQuery.value.trim()) return
  router.push({
    path: '/search',
    query: { q: inputQuery.value.trim() },
  })
}
</script>

<template>
  <div class="min-h-screen bg-surface-50 pt-20 pb-20">
    <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
      <!-- 검색 헤더 및 입력 바 -->
      <div class="max-w-2xl mx-auto my-8 text-center">
        <h1 class="text-3xl font-black text-surface-900 mb-6">
          {{ t('catalog.searchTitle') }}
        </h1>

        <form @submit.prevent="onSearchSubmit" class="flex items-center bg-white rounded-2xl shadow-xl shadow-surface-900/5 border border-surface-200 p-2 focus-within:border-primary-500 transition-all">
          <div class="flex items-center gap-3 px-4 flex-1">
            <span class="text-surface-400 text-lg">🔍</span>
            <input
              v-model="inputQuery"
              type="text"
              :placeholder="t('catalog.searchPlaceholder')"
              class="w-full py-2.5 text-surface-900 placeholder-surface-400 outline-none bg-transparent text-base"
            />
          </div>
          <button type="submit" class="btn-primary !rounded-xl !py-2.5 !px-6 shrink-0">
            {{ t('common.search') }}
          </button>
        </form>
      </div>

      <!-- 현재 검색어 안내 -->
      <div v-if="searchQuery" class="flex items-center justify-between border-b border-surface-200 pb-4 mb-8">
        <div class="flex items-center gap-2">
          <span class="text-surface-500 text-sm">검색어:</span>
          <span class="px-3 py-1 bg-primary-50 text-primary-600 font-bold rounded-lg text-sm">
            "{{ searchQuery }}"
          </span>
        </div>

        <!-- 탭 전환 버튼 -->
        <div class="flex items-center gap-2 p-1 bg-surface-200/60 rounded-xl">
          <button
            type="button"
            class="px-4 py-1.5 rounded-lg text-sm font-bold transition-all"
            :class="activeTab === 'restaurants'
              ? 'bg-white text-surface-900 shadow-sm'
              : 'text-surface-600 hover:text-surface-900'"
            @click="activeTab = 'restaurants'"
          >
            {{ t('catalog.searchTabRestaurants') }} ({{ restaurantResults.length }})
          </button>
          <button
            type="button"
            class="px-4 py-1.5 rounded-lg text-sm font-bold transition-all"
            :class="activeTab === 'foods'
              ? 'bg-white text-surface-900 shadow-sm'
              : 'text-surface-600 hover:text-surface-900'"
            @click="activeTab = 'foods'"
          >
            {{ t('catalog.searchTabFoods') }} ({{ foodResults.length }})
          </button>
        </div>
      </div>

      <!-- 로딩 인디케이터 -->
      <div v-if="isLoading" class="py-16 text-center text-surface-400">
        <div class="inline-block w-8 h-8 border-4 border-primary-500 border-t-transparent rounded-full animate-spin mb-4"></div>
        <p class="text-sm">검색 결과를 불러오는 중...</p>
      </div>

      <!-- 검색어 미입력 상태 -->
      <div v-else-if="!searchQuery" class="card p-12 text-center my-8 text-surface-500">
        <div class="text-4xl mb-3">💡</div>
        <p>원하는 음식점이나 메뉴 이름을 입력해주세요.</p>
      </div>

      <!-- 검색 결과가 없을 때 -->
      <div
        v-else-if="(activeTab === 'restaurants' && restaurantResults.length === 0) || (activeTab === 'foods' && foodResults.length === 0)"
        class="card p-12 text-center my-8"
      >
        <div class="text-5xl mb-4">😶</div>
        <h3 class="text-lg font-bold text-surface-800 mb-2">
          {{ t('catalog.searchEmpty') }}
        </h3>
        <p class="text-surface-500 text-sm mb-6">
          다른 검색어를 입력해보시거나, 철자를 확인해주세요.
        </p>
        <NuxtLink to="/restaurants" class="btn-primary !px-6 !py-2.5 !rounded-xl text-sm">
          전체 맛집 보러가기
        </NuxtLink>
      </div>

      <!-- 결과 목록 -->
      <div v-else>
        <!-- 1. 음식점 탭 결과 -->
        <div v-if="activeTab === 'restaurants'" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <NuxtLink
            v-for="restaurant in restaurantResults"
            :key="restaurant.id"
            :to="`/restaurants/${restaurant.id}`"
            class="group card overflow-hidden hover:-translate-y-1 hover:shadow-xl transition-all duration-300 flex flex-col bg-white"
          >
            <div class="relative h-44 w-full overflow-hidden bg-surface-100">
              <img
                v-if="restaurant.imageUrl"
                :src="restaurant.imageUrl"
                :alt="restaurant.name"
                class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                loading="lazy"
              />
              <div v-else class="w-full h-full flex items-center justify-center text-4xl bg-surface-100">
                🍽️
              </div>
              <div class="absolute top-3 left-3 px-3 py-1 rounded-full bg-surface-900/70 backdrop-blur-md text-white text-xs font-semibold">
                {{ t(`category.${restaurant.categoryCode}`) }}
              </div>
            </div>

            <div class="p-5 flex-1 flex flex-col justify-between">
              <div>
                <h3 class="text-lg font-bold text-surface-900 group-hover:text-primary-600 transition-colors line-clamp-1 mb-1">
                  {{ restaurant.name }}
                </h3>
                <p class="text-sm text-surface-500 line-clamp-2 mb-4">
                  {{ restaurant.description || restaurant.address }}
                </p>
              </div>

              <div class="pt-3 border-t border-surface-100 flex items-center justify-between text-xs text-surface-600 font-medium">
                <div>
                  {{ t('catalog.deliveryFee') }}:
                  <span class="font-bold text-surface-900">
                    {{ restaurant.deliveryFee > 0 ? `${restaurant.deliveryFee.toLocaleString()}${t('catalog.currency')}` : t('catalog.freeDelivery') }}
                  </span>
                </div>
                <div>
                  {{ t('catalog.minOrderAmount') }}:
                  <span class="font-bold text-surface-900">
                    {{ restaurant.minOrderAmount.toLocaleString() }}{{ t('catalog.currency') }}
                  </span>
                </div>
              </div>
            </div>
          </NuxtLink>
        </div>

        <!-- 2. 메뉴 탭 결과 -->
        <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <NuxtLink
            v-for="food in foodResults"
            :key="food.id"
            :to="`/restaurants/${food.restaurantId}`"
            class="card p-4 sm:p-5 flex gap-4 bg-white hover:border-primary-200 hover:shadow-lg transition-all group"
          >
            <div class="relative w-24 h-24 rounded-xl overflow-hidden shrink-0 bg-surface-100">
              <img
                v-if="food.imageUrl"
                :src="food.imageUrl"
                :alt="food.name"
                class="w-full h-full object-cover group-hover:scale-105 transition-transform"
                loading="lazy"
              />
              <div v-else class="w-full h-full flex items-center justify-center text-3xl">
                🍲
              </div>
              <div
                v-if="food.soldOut"
                class="absolute inset-0 bg-surface-950/60 flex items-center justify-center"
              >
                <span class="px-2 py-0.5 rounded bg-red-500 text-white text-[10px] font-bold">
                  {{ t('catalog.soldOut') }}
                </span>
              </div>
            </div>

            <div class="flex-1 flex flex-col justify-between">
              <div>
                <h3 class="font-bold text-base sm:text-lg text-surface-900 group-hover:text-primary-600 transition-colors mb-1">
                  {{ food.name }}
                </h3>
                <p class="text-xs sm:text-sm text-surface-500 line-clamp-2">
                  {{ food.description }}
                </p>
              </div>

              <div class="flex items-center justify-between mt-3 pt-2 border-t border-surface-100 text-xs">
                <span class="font-extrabold text-sm sm:text-base text-surface-900">
                  {{ food.price.toLocaleString() }}{{ t('catalog.currency') }}
                </span>
                <span class="text-primary-600 font-semibold group-hover:underline">
                  음식점 바로가기 →
                </span>
              </div>
            </div>
          </NuxtLink>
        </div>
      </div>
    </div>
  </div>
</template>
