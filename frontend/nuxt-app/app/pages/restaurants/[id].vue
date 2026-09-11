<script setup lang="ts">
import { ref } from 'vue'

const { t } = useI18n()
const route = useRoute()
const { useRestaurantDetailFetch } = useCatalog()

const restaurantId = route.params.id as string
const { data: detailData, pending, error } = await useRestaurantDetailFetch(restaurantId)

const restaurant = computed(() => detailData.value?.restaurant)
const foods = computed(() => detailData.value?.foods || [])

useHead({
  title: () => restaurant.value ? `${restaurant.value.name} - RTC Delivery` : '음식점 상세',
})

// 토스트 메시지 상태
const toastMessage = ref<string | null>(null)
let toastTimer: any = null

function onAddToCart(foodName: string) {
  toastMessage.value = `${foodName} ${t('catalog.addedToCart')}`
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = null
  }, 2500)
}
</script>

<template>
  <div class="min-h-screen bg-surface-50 pt-20 pb-20">
    <!-- 토스트 알림 -->
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="transform translate-y-4 opacity-0"
      enter-to-class="transform translate-y-0 opacity-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="transform translate-y-0 opacity-100"
      leave-to-class="transform translate-y-4 opacity-0"
    >
      <div
        v-if="toastMessage"
        class="fixed bottom-8 right-8 z-50 flex items-center gap-3 bg-surface-900 text-white px-5 py-3.5 rounded-2xl shadow-2xl border border-white/10 text-sm font-medium"
        role="status"
      >
        <span class="text-primary-400 text-lg">🛒</span>
        <span>{{ toastMessage }}</span>
      </div>
    </Transition>

    <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
      <!-- 상단 뒤로가기 -->
      <div class="mb-6">
        <NuxtLink
          to="/restaurants"
          class="inline-flex items-center gap-2 text-sm font-medium text-surface-500 hover:text-primary-600 transition-colors"
        >
          <span>←</span>
          <span>{{ t('catalog.title') }} 목록으로</span>
        </NuxtLink>
      </div>

      <!-- 로딩 중 스켈레톤 -->
      <div v-if="pending" class="card p-8 animate-pulse mb-8">
        <div class="h-64 bg-surface-200 rounded-2xl mb-6"></div>
        <div class="h-8 bg-surface-200 rounded w-1/3 mb-4"></div>
        <div class="h-4 bg-surface-200 rounded w-2/3"></div>
      </div>

      <!-- 에러 또는 리소스 없음 -->
      <div
        v-else-if="error || !restaurant"
        class="card p-12 text-center my-12"
      >
        <div class="text-5xl mb-4">🏚️</div>
        <h2 class="text-xl font-bold text-surface-800 mb-2">
          {{ t('error.RESTAURANT_NOT_FOUND') }}
        </h2>
        <p class="text-surface-500 text-sm mb-6">
          요청하신 음식점이 존재하지 않거나 현재 운영하지 않습니다.
        </p>
        <NuxtLink
          to="/restaurants"
          class="btn-primary !px-6 !py-2.5 !rounded-xl text-sm"
        >
          다른 맛집 둘러보기
        </NuxtLink>
      </div>

      <!-- 매장 상세 본문 -->
      <div v-else>
        <!-- 히어로 배너 카드 -->
        <div class="card overflow-hidden bg-white shadow-xl shadow-surface-900/5 mb-10">
          <div class="relative h-64 sm:h-80 w-full overflow-hidden bg-surface-100">
            <img
              v-if="restaurant.imageUrl"
              :src="restaurant.imageUrl"
              :alt="restaurant.name"
              class="w-full h-full object-cover"
            />
            <div
              v-else
              class="w-full h-full flex items-center justify-center bg-gradient-to-br from-primary-500/10 via-accent-500/10 to-surface-100 text-6xl"
            >
              🥘
            </div>
            <div class="absolute inset-0 bg-gradient-to-t from-surface-950/80 via-surface-950/20 to-transparent"></div>

            <div class="absolute bottom-6 left-6 right-6 text-white">
              <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-primary-500 text-xs font-semibold mb-2">
                {{ t(`category.${restaurant.categoryCode}`) }}
              </div>
              <h1 class="text-2xl sm:text-4xl font-black mb-2 drop-shadow-md">
                {{ restaurant.name }}
              </h1>
              <p v-if="restaurant.description" class="text-sm sm:text-base text-white/90 line-clamp-2 max-w-2xl drop-shadow">
                {{ restaurant.description }}
              </p>
            </div>
          </div>

          <!-- 매장 기본 정보 바 -->
          <div class="p-6 grid grid-cols-2 sm:grid-cols-4 gap-4 bg-surface-50/50 border-t border-surface-100 text-sm">
            <div>
              <p class="text-xs text-surface-400 mb-1 font-medium">{{ t('catalog.deliveryFee') }}</p>
              <p class="font-bold text-surface-900">
                {{ restaurant.deliveryFee > 0 ? `${restaurant.deliveryFee.toLocaleString()}${t('catalog.currency')}` : t('catalog.freeDelivery') }}
              </p>
            </div>

            <div>
              <p class="text-xs text-surface-400 mb-1 font-medium">{{ t('catalog.minOrderAmount') }}</p>
              <p class="font-bold text-surface-900">
                {{ restaurant.minOrderAmount.toLocaleString() }}{{ t('catalog.currency') }}
              </p>
            </div>

            <div>
              <p class="text-xs text-surface-400 mb-1 font-medium">{{ t('catalog.phoneNumber') }}</p>
              <p class="font-semibold text-surface-800 truncate">
                {{ restaurant.phoneNumber || '-' }}
              </p>
            </div>

            <div>
              <p class="text-xs text-surface-400 mb-1 font-medium">{{ t('catalog.address') }}</p>
              <p class="font-semibold text-surface-800 truncate" :title="restaurant.address">
                {{ restaurant.address }}
              </p>
            </div>
          </div>
        </div>

        <!-- 메뉴 목록 섹션 -->
        <div>
          <div class="flex items-center justify-between mb-6">
            <h2 class="text-2xl font-bold text-surface-900 flex items-center gap-2">
              <span>메뉴</span>
              <span class="text-sm font-normal text-surface-500">({{ foods.length }}개)</span>
            </h2>
          </div>

          <!-- 메뉴 없음 -->
          <div
            v-if="foods.length === 0"
            class="card p-12 text-center text-surface-500 text-sm"
          >
            {{ t('catalog.noFoods') }}
          </div>

          <!-- 메뉴 카드 그리드 -->
          <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-6">
            <div
              v-for="food in foods"
              :key="food.id"
              class="card p-4 sm:p-5 flex gap-4 bg-white transition-all duration-200 relative overflow-hidden"
              :class="food.soldOut ? 'bg-surface-50 border-dashed border-surface-300' : 'hover:border-primary-200 hover:shadow-lg'"
            >
              <!-- 썸네일 -->
              <div class="relative w-24 h-24 sm:w-28 sm:h-28 rounded-xl overflow-hidden shrink-0 bg-surface-100">
                <img
                  v-if="food.imageUrl"
                  :src="food.imageUrl"
                  :alt="food.name"
                  class="w-full h-full object-cover"
                  :class="food.soldOut ? 'grayscale opacity-50' : ''"
                  loading="lazy"
                />
                <div
                  v-else
                  class="w-full h-full flex items-center justify-center text-3xl"
                  :class="food.soldOut ? 'grayscale opacity-50' : ''"
                >
                  🍲
                </div>

                <!-- 품절 오버레이 -->
                <div
                  v-if="food.soldOut"
                  class="absolute inset-0 bg-surface-950/60 backdrop-blur-[2px] flex items-center justify-center"
                >
                  <span class="px-2 py-1 rounded bg-red-500 text-white text-[11px] font-bold tracking-wider">
                    {{ t('catalog.soldOut') }}
                  </span>
                </div>
              </div>

              <!-- 메뉴 정보 -->
              <div class="flex-1 flex flex-col justify-between">
                <div>
                  <div class="flex items-start justify-between gap-2 mb-1">
                    <h3
                      class="font-bold text-base sm:text-lg"
                      :class="food.soldOut ? 'text-surface-400 line-through' : 'text-surface-900'"
                    >
                      {{ food.name }}
                    </h3>
                  </div>
                  <p class="text-xs sm:text-sm text-surface-500 line-clamp-2 mb-2">
                    {{ food.description }}
                  </p>
                </div>

                <div class="flex items-center justify-between mt-2">
                  <span
                    class="font-extrabold text-base sm:text-lg"
                    :class="food.soldOut ? 'text-surface-400' : 'text-surface-900'"
                  >
                    {{ food.price.toLocaleString() }}{{ t('catalog.currency') }}
                  </span>

                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all duration-200"
                    :class="food.soldOut
                      ? 'bg-surface-200 text-surface-400 cursor-not-allowed'
                      : 'bg-primary-50 text-primary-600 hover:bg-primary-500 hover:text-white active:scale-95 shadow-sm'"
                    :disabled="food.soldOut"
                    @click="onAddToCart(food.name)"
                  >
                    <span>+</span>
                    <span>{{ t('catalog.addToCart') }}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
