<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { RestaurantResponse, CategoryResponse, RestaurantCreateRequest } from '~/types/catalog'

definePageMeta({
  middleware: 'owner',
})

const { t } = useI18n()
const { getMyRestaurants, createRestaurant, deleteRestaurant, isSubmitting } = useOwnerStore()
const { getCategories } = useCatalog()

useHead({
  title: () => `${t('owner.title')} - RTC Delivery`,
})

const restaurants = ref<RestaurantResponse[]>([])
const categories = ref<CategoryResponse[]>([])
const isLoading = ref(true)
const showModal = ref(false)
const formError = ref<string | null>(null)

// 신규 매장 등록 폼 데이터
const form = ref<RestaurantCreateRequest>({
  categoryId: 1,
  name: '',
  description: '',
  address: '',
  phoneNumber: '',
  deliveryFee: 3000,
  minOrderAmount: 12000,
  imageUrl: '',
})

async function loadData() {
  isLoading.value = true
  try {
    const [restRes, catRes] = await Promise.all([
      getMyRestaurants(),
      getCategories(),
    ])
    restaurants.value = restRes.content || []
    categories.value = catRes || []
    if (categories.value.length > 0 && !form.value.categoryId) {
      form.value.categoryId = categories.value[0].id
    }
  } catch (e) {
    // 에러 처리
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  loadData()
})

function openNewStoreModal() {
  form.value = {
    categoryId: categories.value[0]?.id || 1,
    name: '',
    description: '',
    address: '',
    phoneNumber: '',
    deliveryFee: 3000,
    minOrderAmount: 12000,
    imageUrl: '',
  }
  formError.value = null
  showModal.value = true
}

async function onSubmitCreate() {
  if (!form.value.name.trim() || !form.value.address.trim()) {
    formError.value = '매장명과 주소는 필수 입력값입니다.'
    return
  }

  formError.value = null
  try {
    await createRestaurant({
      ...form.value,
      name: form.value.name.trim(),
      address: form.value.address.trim(),
      description: form.value.description?.trim() || undefined,
      phoneNumber: form.value.phoneNumber?.trim() || undefined,
      imageUrl: form.value.imageUrl?.trim() || undefined,
    })
    showModal.value = false
    await loadData()
  } catch (e: any) {
    formError.value = e?.message || '매장 등록 중 오류가 발생했습니다.'
  }
}

async function onDeactivate(id: number, name: string) {
  if (!confirm(`'${name}' ${t('owner.deactivateConfirm')}`)) return
  try {
    await deleteRestaurant(id)
    await loadData()
  } catch (e) {
    alert('매장 비활성화에 실패했습니다.')
  }
}
</script>

<template>
  <div class="min-h-screen bg-surface-50 pt-20 pb-20">
    <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
      <!-- 헤더 영역 -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-primary-50 text-primary-600 text-xs font-bold mb-2">
            👨‍🍳 점주 / 관리자 전용
          </div>
          <h1 class="text-3xl font-black text-surface-900">
            {{ t('owner.title') }}
          </h1>
          <p class="text-surface-500 text-sm mt-1">
            등록된 매장과 메뉴를 실시간으로 관리하고 품절 상태를 제어하세요.
          </p>
        </div>

        <button
          type="button"
          class="btn-primary !rounded-xl !py-3 !px-5 inline-flex items-center gap-2 self-start sm:self-auto shadow-lg shadow-primary-500/20"
          @click="openNewStoreModal"
        >
          <span>✨</span>
          <span>{{ t('owner.newRestaurant') }}</span>
        </button>
      </div>

      <!-- 로딩 인디케이터 -->
      <div v-if="isLoading" class="card p-16 text-center text-surface-400 animate-pulse">
        <div class="inline-block w-8 h-8 border-4 border-primary-500 border-t-transparent rounded-full animate-spin mb-4"></div>
        <p class="text-sm">매장 정보를 불러오는 중입니다...</p>
      </div>

      <!-- 등록된 매장 없음 -->
      <div
        v-else-if="restaurants.length === 0"
        class="card p-12 text-center my-8 bg-white"
      >
        <div class="text-5xl mb-4">🏪</div>
        <h3 class="text-lg font-bold text-surface-800 mb-2">
          {{ t('owner.noRestaurants') }}
        </h3>
        <p class="text-surface-500 text-sm mb-6">
          첫 번째 매장을 등록하고 맛있는 메뉴를 판매해보세요!
        </p>
        <button
          type="button"
          class="btn-primary !px-6 !py-2.5 !rounded-xl text-sm"
          @click="openNewStoreModal"
        >
          {{ t('owner.newRestaurant') }}
        </button>
      </div>

      <!-- 매장 카드 그리드 -->
      <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <div
          v-for="rest in restaurants"
          :key="rest.id"
          class="card overflow-hidden bg-white hover:shadow-xl transition-all flex flex-col justify-between border border-surface-200/80"
        >
          <div>
            <div class="relative h-40 w-full overflow-hidden bg-surface-100">
              <img
                v-if="rest.imageUrl"
                :src="rest.imageUrl"
                :alt="rest.name"
                class="w-full h-full object-cover"
              />
              <div v-else class="w-full h-full flex items-center justify-center text-4xl bg-gradient-to-br from-primary-50 to-accent-50 text-surface-400">
                🏪
              </div>

              <div class="absolute top-3 left-3 px-2.5 py-1 rounded-full bg-surface-900/75 backdrop-blur-md text-white text-xs font-semibold">
                {{ t(`category.${rest.categoryCode}`) }}
              </div>
            </div>

            <div class="p-5">
              <h3 class="text-lg font-bold text-surface-900 mb-1 line-clamp-1">
                {{ rest.name }}
              </h3>
              <p class="text-xs text-surface-500 line-clamp-2 mb-4">
                {{ rest.description || rest.address }}
              </p>

              <div class="space-y-1.5 text-xs text-surface-600 bg-surface-50 p-3 rounded-xl">
                <div class="flex justify-between">
                  <span>{{ t('catalog.deliveryFee') }}:</span>
                  <span class="font-bold text-surface-800">{{ rest.deliveryFee.toLocaleString() }}원</span>
                </div>
                <div class="flex justify-between">
                  <span>{{ t('catalog.minOrderAmount') }}:</span>
                  <span class="font-bold text-surface-800">{{ rest.minOrderAmount.toLocaleString() }}원</span>
                </div>
                <div class="flex justify-between">
                  <span>{{ t('catalog.phoneNumber') }}:</span>
                  <span class="text-surface-700 truncate">{{ rest.phoneNumber || '-' }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 하단 액션 버튼 -->
          <div class="p-5 pt-0 flex items-center gap-2">
            <NuxtLink
              :to="`/owner/restaurants/${rest.id}`"
              class="flex-1 text-center py-2 px-3 rounded-xl bg-primary-50 text-primary-600 hover:bg-primary-500 hover:text-white font-bold text-xs transition-colors"
            >
              매장 및 메뉴 관리 →
            </NuxtLink>
            <button
              type="button"
              class="py-2 px-3 rounded-xl border border-red-200 text-red-500 hover:bg-red-50 font-medium text-xs transition-colors"
              @click="onDeactivate(rest.id, rest.name)"
            >
              비활성화
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 신규 매장 등록 모달 -->
    <div
      v-if="showModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-surface-950/50 backdrop-blur-sm"
    >
      <div class="card w-full max-w-lg bg-white p-6 sm:p-8 rounded-2xl shadow-2xl max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between pb-4 border-b border-surface-100 mb-6">
          <h2 class="text-xl font-bold text-surface-900">
            {{ t('owner.newRestaurant') }}
          </h2>
          <button
            type="button"
            class="text-surface-400 hover:text-surface-600 text-xl font-bold p-1"
            @click="showModal = false"
          >
            ✕
          </button>
        </div>

        <div v-if="formError" class="mb-4 p-3 rounded-xl bg-red-50 text-red-600 text-xs font-medium" role="alert">
          {{ formError }}
        </div>

        <form @submit.prevent="onSubmitCreate" class="space-y-4">
          <div>
            <label class="block text-xs font-bold text-surface-700 mb-1">
              {{ t('owner.categoryLabel') }} *
            </label>
            <select
              v-model="form.categoryId"
              class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
            >
              <option v-for="cat in categories" :key="cat.id" :value="cat.id">
                {{ t(`category.${cat.code}`) }} ({{ cat.name }})
              </option>
            </select>
          </div>

          <div>
            <label class="block text-xs font-bold text-surface-700 mb-1">
              {{ t('owner.nameLabel') }} *
            </label>
            <input
              v-model="form.name"
              type="text"
              placeholder="예: 서울 수제버거 본점"
              required
              class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
            />
          </div>

          <div>
            <label class="block text-xs font-bold text-surface-700 mb-1">
              {{ t('owner.descriptionLabel') }}
            </label>
            <textarea
              v-model="form.description"
              rows="2"
              placeholder="매장 소개를 입력하세요"
              class="w-full py-2 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
            ></textarea>
          </div>

          <div>
            <label class="block text-xs font-bold text-surface-700 mb-1">
              {{ t('owner.addressLabel') }} *
            </label>
            <input
              v-model="form.address"
              type="text"
              placeholder="예: 서울시 강남구 테헤란로 123"
              required
              class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
            />
          </div>

          <div>
            <label class="block text-xs font-bold text-surface-700 mb-1">
              {{ t('owner.phoneLabel') }}
            </label>
            <input
              v-model="form.phoneNumber"
              type="text"
              placeholder="예: 02-1234-5678"
              class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
            />
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-bold text-surface-700 mb-1">
                {{ t('owner.deliveryFeeLabel') }} *
              </label>
              <input
                v-model.number="form.deliveryFee"
                type="number"
                min="0"
                step="500"
                required
                class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
              />
            </div>

            <div>
              <label class="block text-xs font-bold text-surface-700 mb-1">
                {{ t('owner.minOrderAmountLabel') }} *
              </label>
              <input
                v-model.number="form.minOrderAmount"
                type="number"
                min="0"
                step="1000"
                required
                class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
              />
            </div>
          </div>

          <div>
            <label class="block text-xs font-bold text-surface-700 mb-1">
              {{ t('owner.imageUrlLabel') }}
            </label>
            <input
              v-model="form.imageUrl"
              type="url"
              placeholder="https://images.unsplash.com/..."
              class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
            />
          </div>

          <div class="flex items-center justify-end gap-3 pt-4 border-t border-surface-100">
            <button
              type="button"
              class="px-5 py-2.5 rounded-xl border border-surface-200 text-surface-600 hover:bg-surface-50 text-sm font-medium"
              @click="showModal = false"
            >
              {{ t('owner.cancel') }}
            </button>
            <button
              type="submit"
              class="btn-primary !rounded-xl !py-2.5 !px-6 text-sm"
              :disabled="isSubmitting"
            >
              {{ isSubmitting ? '등록 중...' : t('owner.save') }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
