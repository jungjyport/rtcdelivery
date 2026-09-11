<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type {
  RestaurantResponse,
  FoodResponse,
  FoodCreateRequest,
  FoodUpdateRequest,
  RestaurantUpdateRequest,
} from '~/types/catalog'

definePageMeta({
  middleware: 'owner',
})

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const {
  getRestaurant,
  updateRestaurant,
  deleteRestaurant,
  getRestaurantFoods,
  createFood,
  updateFood,
  deleteFood,
  toggleFoodSoldOut,
  isSubmitting,
} = useOwnerStore()

const restaurantId = Number(route.params.id)
const activeTab = ref<'info' | 'foods'>('foods')

const isLoading = ref(true)
const restaurant = ref<RestaurantResponse | null>(null)
const foods = ref<FoodResponse[]>([])

const toastMessage = ref<string | null>(null)
let toastTimer: any = null

function showToast(msg: string) {
  toastMessage.value = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = null
  }, 2500)
}

// 매장 수정 폼
const storeForm = ref<RestaurantUpdateRequest>({
  name: '',
  description: '',
  address: '',
  phoneNumber: '',
  deliveryFee: 3000,
  minOrderAmount: 12000,
  imageUrl: '',
})

// 메뉴 등록/수정 모달 상태
const showFoodModal = ref(false)
const editingFoodId = ref<number | null>(null)
const foodForm = ref<FoodCreateRequest>({
  name: '',
  description: '',
  price: 10000,
  imageUrl: '',
  displayOrder: 1,
})
const foodModalError = ref<string | null>(null)

async function loadData() {
  isLoading.value = true
  try {
    const [r, f] = await Promise.all([
      getRestaurant(restaurantId),
      getRestaurantFoods(restaurantId),
    ])
    restaurant.value = r
    foods.value = f

    storeForm.value = {
      name: r.name,
      description: r.description || '',
      address: r.address,
      phoneNumber: r.phoneNumber || '',
      deliveryFee: r.deliveryFee,
      minOrderAmount: r.minOrderAmount,
      imageUrl: r.imageUrl || '',
    }
  } catch (e) {
    // 소유권 위반 등 404
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  loadData()
})

// 1. 매장 정보 저장
async function onSaveStore() {
  try {
    const updated = await updateRestaurant(restaurantId, {
      ...storeForm.value,
      name: storeForm.value.name?.trim(),
      address: storeForm.value.address?.trim(),
      description: storeForm.value.description?.trim() || undefined,
      phoneNumber: storeForm.value.phoneNumber?.trim() || undefined,
      imageUrl: storeForm.value.imageUrl?.trim() || undefined,
    })
    restaurant.value = updated
    showToast(t('owner.saveSuccess'))
  } catch (e: any) {
    alert(e?.message || '매장 정보 수정 중 오류가 발생했습니다.')
  }
}

// 매장 비활성화
async function onDeactivateStore() {
  if (!confirm(t('owner.deactivateConfirm'))) return
  try {
    await deleteRestaurant(restaurantId)
    router.push('/owner/restaurants')
  } catch (e: any) {
    alert(e?.message || '비활성화에 실패했습니다.')
  }
}

// 2. 메뉴 모달 열기
function openCreateFoodModal() {
  editingFoodId.value = null
  foodForm.value = {
    name: '',
    description: '',
    price: 9000,
    imageUrl: '',
    displayOrder: foods.value.length + 1,
  }
  foodModalError.value = null
  showFoodModal.value = true
}

function openEditFoodModal(food: FoodResponse) {
  editingFoodId.value = food.id
  foodForm.value = {
    name: food.name,
    description: food.description || '',
    price: food.price,
    imageUrl: food.imageUrl || '',
    displayOrder: food.displayOrder,
  }
  foodModalError.value = null
  showFoodModal.value = true
}

// 메뉴 등록 / 수정 제출
async function onSubmitFood() {
  if (!foodForm.value.name.trim() || foodForm.value.price < 0) {
    foodModalError.value = '메뉴명과 올바른 가격을 입력해주세요.'
    return
  }

  try {
    if (editingFoodId.value) {
      await updateFood(restaurantId, editingFoodId.value, {
        name: foodForm.value.name.trim(),
        description: foodForm.value.description?.trim() || undefined,
        price: foodForm.value.price,
        imageUrl: foodForm.value.imageUrl?.trim() || undefined,
        displayOrder: foodForm.value.displayOrder,
      })
      showToast('메뉴가 수정되었습니다.')
    } else {
      await createFood(restaurantId, {
        name: foodForm.value.name.trim(),
        description: foodForm.value.description?.trim() || undefined,
        price: foodForm.value.price,
        imageUrl: foodForm.value.imageUrl?.trim() || undefined,
        displayOrder: foodForm.value.displayOrder,
      })
      showToast('새 메뉴가 등록되었습니다.')
    }
    showFoodModal.value = false
    foods.value = await getRestaurantFoods(restaurantId)
  } catch (e: any) {
    foodModalError.value = e?.message || '메뉴 저장에 실패했습니다.'
  }
}

// 메뉴 삭제
async function onDeleteFood(foodId: number) {
  if (!confirm(t('owner.deleteConfirm'))) return
  try {
    await deleteFood(restaurantId, foodId)
    foods.value = await getRestaurantFoods(restaurantId)
    showToast('메뉴가 삭제되었습니다.')
  } catch (e: any) {
    alert(e?.message || '메뉴 삭제에 실패했습니다.')
  }
}

// 품절 원클릭 토글
async function onToggleSoldOut(food: FoodResponse) {
  try {
    const updated = await toggleFoodSoldOut(restaurantId, food.id, food.soldOut)
    food.soldOut = updated.soldOut
    showToast(updated.soldOut ? '품절로 변경되었습니다.' : '판매중으로 변경되었습니다.')
  } catch (e: any) {
    alert(e?.message || '상태 변경에 실패했습니다.')
  }
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
      >
        <span>✅</span>
        <span>{{ toastMessage }}</span>
      </div>
    </Transition>

    <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
      <!-- 상단 뒤로가기 -->
      <div class="mb-6">
        <NuxtLink
          to="/owner/restaurants"
          class="inline-flex items-center gap-2 text-sm font-medium text-surface-500 hover:text-primary-600 transition-colors"
        >
          <span>←</span>
          <span>내 매장 목록으로</span>
        </NuxtLink>
      </div>

      <!-- 로딩 중 -->
      <div v-if="isLoading" class="card p-16 text-center text-surface-400 animate-pulse">
        <div class="inline-block w-8 h-8 border-4 border-primary-500 border-t-transparent rounded-full animate-spin mb-4"></div>
        <p class="text-sm">매장 상세 정보를 불러오는 중입니다...</p>
      </div>

      <!-- 매장 정보를 찾을 수 없음 (소유권 위반 포함) -->
      <div v-else-if="!restaurant" class="card p-12 text-center my-8 bg-white">
        <div class="text-5xl mb-4">🔒</div>
        <h2 class="text-xl font-bold text-surface-800 mb-2">
          매장 정보를 불러올 수 없습니다
        </h2>
        <p class="text-surface-500 text-sm mb-6">
          해당 매장이 존재하지 않거나, 본인 소유의 매장이 아닙니다.
        </p>
        <NuxtLink to="/owner/restaurants" class="btn-primary !px-6 !py-2.5 !rounded-xl text-sm">
          내 매장 목록으로 돌아가기
        </NuxtLink>
      </div>

      <!-- 매장 관리 본문 -->
      <div v-else>
        <!-- 헤더 바 -->
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6 pb-4 border-b border-surface-200">
          <div>
            <div class="flex items-center gap-3 mb-1">
              <h1 class="text-2xl sm:text-3xl font-black text-surface-900">
                {{ restaurant.name }}
              </h1>
              <span class="px-2.5 py-1 rounded-full bg-primary-50 text-primary-600 font-bold text-xs">
                {{ t(`category.${restaurant.categoryCode}`) }}
              </span>
            </div>
            <p class="text-surface-500 text-xs sm:text-sm">
              {{ restaurant.address }}
            </p>
          </div>

          <!-- 탭 선택 -->
          <div class="flex items-center gap-2 p-1 bg-surface-200/60 rounded-xl self-start sm:self-auto">
            <button
              type="button"
              class="px-4 py-2 rounded-lg text-sm font-bold transition-all"
              :class="activeTab === 'foods'
                ? 'bg-white text-surface-900 shadow-sm'
                : 'text-surface-600 hover:text-surface-900'"
              @click="activeTab = 'foods'"
            >
              {{ t('owner.manageMenu') }} ({{ foods.length }})
            </button>
            <button
              type="button"
              class="px-4 py-2 rounded-lg text-sm font-bold transition-all"
              :class="activeTab === 'info'
                ? 'bg-white text-surface-900 shadow-sm'
                : 'text-surface-600 hover:text-surface-900'"
              @click="activeTab = 'info'"
            >
              {{ t('owner.editRestaurant') }}
            </button>
          </div>
        </div>

        <!-- 탭 1: 메뉴 관리 -->
        <div v-if="activeTab === 'foods'">
          <div class="flex items-center justify-between mb-6">
            <h2 class="text-xl font-bold text-surface-900">
              메뉴 목록
            </h2>
            <button
              type="button"
              class="btn-primary !rounded-xl !py-2.5 !px-5 text-sm inline-flex items-center gap-2 shadow-md shadow-primary-500/20"
              @click="openCreateFoodModal"
            >
              <span>+</span>
              <span>{{ t('owner.addFood') }}</span>
            </button>
          </div>

          <!-- 메뉴 없음 -->
          <div v-if="foods.length === 0" class="card p-12 text-center text-surface-500 text-sm bg-white">
            <p class="mb-4">{{ t('owner.noFoods') }}</p>
            <button
              type="button"
              class="btn-primary !px-5 !py-2 text-xs !rounded-lg"
              @click="openCreateFoodModal"
            >
              첫 메뉴 등록하기
            </button>
          </div>

          <!-- 메뉴 리스트 -->
          <div v-else class="space-y-3">
            <div
              v-for="food in foods"
              :key="food.id"
              class="card p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white transition-all"
              :class="food.soldOut ? 'bg-surface-50/80 border-surface-300' : 'hover:border-surface-300'"
            >
              <div class="flex items-center gap-4">
                <div class="w-16 h-16 rounded-xl overflow-hidden bg-surface-100 shrink-0 relative">
                  <img
                    v-if="food.imageUrl"
                    :src="food.imageUrl"
                    :alt="food.name"
                    class="w-full h-full object-cover"
                    :class="food.soldOut ? 'grayscale opacity-60' : ''"
                  />
                  <div v-else class="w-full h-full flex items-center justify-center text-2xl">
                    🍲
                  </div>
                  <div
                    v-if="food.soldOut"
                    class="absolute inset-0 bg-red-500/80 text-white text-[10px] font-bold flex items-center justify-center"
                  >
                    품절
                  </div>
                </div>

                <div>
                  <div class="flex items-center gap-2">
                    <h3 class="font-bold text-base text-surface-900" :class="food.soldOut ? 'text-surface-500' : ''">
                      {{ food.name }}
                    </h3>
                  </div>
                  <p class="text-xs text-surface-500 line-clamp-1 mb-1">
                    {{ food.description || '-' }}
                  </p>
                  <p class="font-extrabold text-sm text-surface-900">
                    {{ food.price.toLocaleString() }}원
                  </p>
                </div>
              </div>

              <!-- 우측 액션: 품절 토글 & 수정 & 삭제 -->
              <div class="flex items-center gap-2 self-end sm:self-auto">
                <!-- 품절 토글 버튼 -->
                <button
                  type="button"
                  class="px-3 py-1.5 rounded-lg text-xs font-bold transition-all"
                  :class="food.soldOut
                    ? 'bg-red-100 text-red-700 hover:bg-red-200'
                    : 'bg-emerald-50 text-emerald-600 hover:bg-emerald-100'"
                  @click="onToggleSoldOut(food)"
                >
                  {{ food.soldOut ? t('owner.inStockToggle') : t('owner.soldOutToggle') }}
                </button>

                <button
                  type="button"
                  class="px-3 py-1.5 rounded-lg border border-surface-200 text-surface-700 hover:bg-surface-50 text-xs font-semibold"
                  @click="openEditFoodModal(food)"
                >
                  {{ t('owner.editFood') }}
                </button>

                <button
                  type="button"
                  class="px-3 py-1.5 rounded-lg border border-red-200 text-red-500 hover:bg-red-50 text-xs font-semibold"
                  @click="onDeleteFood(food.id)"
                >
                  {{ t('owner.deleteFood') }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 탭 2: 매장 정보 수정 -->
        <div v-else class="card bg-white p-6 sm:p-8">
          <h2 class="text-xl font-bold text-surface-900 mb-6">
            {{ t('owner.editRestaurant') }}
          </h2>

          <form @submit.prevent="onSaveStore" class="space-y-4 max-w-2xl">
            <div>
              <label class="block text-xs font-bold text-surface-700 mb-1">
                {{ t('owner.nameLabel') }} *
              </label>
              <input
                v-model="storeForm.name"
                type="text"
                required
                class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
              />
            </div>

            <div>
              <label class="block text-xs font-bold text-surface-700 mb-1">
                {{ t('owner.descriptionLabel') }}
              </label>
              <textarea
                v-model="storeForm.description"
                rows="3"
                class="w-full py-2 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
              ></textarea>
            </div>

            <div>
              <label class="block text-xs font-bold text-surface-700 mb-1">
                {{ t('owner.addressLabel') }} *
              </label>
              <input
                v-model="storeForm.address"
                type="text"
                required
                class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
              />
            </div>

            <div>
              <label class="block text-xs font-bold text-surface-700 mb-1">
                {{ t('owner.phoneLabel') }}
              </label>
              <input
                v-model="storeForm.phoneNumber"
                type="text"
                class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
              />
            </div>

            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-bold text-surface-700 mb-1">
                  {{ t('owner.deliveryFeeLabel') }} *
                </label>
                <input
                  v-model.number="storeForm.deliveryFee"
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
                  v-model.number="storeForm.minOrderAmount"
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
                v-model="storeForm.imageUrl"
                type="url"
                class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
              />
            </div>

            <div class="pt-4 flex items-center gap-3">
              <button
                type="submit"
                class="btn-primary !rounded-xl !py-2.5 !px-6 text-sm"
                :disabled="isSubmitting"
              >
                {{ isSubmitting ? '저장 중...' : t('owner.save') }}
              </button>
            </div>
          </form>

          <!-- 위험 구역 -->
          <div class="mt-12 pt-6 border-t border-red-100">
            <h3 class="text-sm font-bold text-red-600 mb-2">
              {{ t('owner.dangerZone') }}
            </h3>
            <p class="text-xs text-surface-500 mb-4">
              매장을 비활성화하면 고객 맛집 목록에서 제외되며, 등록된 메뉴는 유지되지만 노출되지 않습니다.
            </p>
            <button
              type="button"
              class="px-4 py-2 rounded-xl border border-red-300 text-red-600 hover:bg-red-50 text-xs font-bold transition-colors"
              @click="onDeactivateStore"
            >
              {{ t('owner.deactivateRestaurant') }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 메뉴 등록/수정 모달 -->
    <div
      v-if="showFoodModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-surface-950/50 backdrop-blur-sm"
    >
      <div class="card w-full max-w-md bg-white p-6 sm:p-8 rounded-2xl shadow-2xl">
        <div class="flex items-center justify-between pb-3 border-b border-surface-100 mb-4">
          <h3 class="text-lg font-bold text-surface-900">
            {{ editingFoodId ? t('owner.editFood') : t('owner.addFood') }}
          </h3>
          <button
            type="button"
            class="text-surface-400 hover:text-surface-600 text-lg font-bold p-1"
            @click="showFoodModal = false"
          >
            ✕
          </button>
        </div>

        <div v-if="foodModalError" class="mb-4 p-3 rounded-xl bg-red-50 text-red-600 text-xs font-medium" role="alert">
          {{ foodModalError }}
        </div>

        <form @submit.prevent="onSubmitFood" class="space-y-4">
          <div>
            <label class="block text-xs font-bold text-surface-700 mb-1">
              {{ t('owner.foodNameLabel') }} *
            </label>
            <input
              v-model="foodForm.name"
              type="text"
              required
              class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
            />
          </div>

          <div>
            <label class="block text-xs font-bold text-surface-700 mb-1">
              {{ t('owner.priceLabel') }} *
            </label>
            <input
              v-model.number="foodForm.price"
              type="number"
              min="0"
              step="500"
              required
              class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
            />
          </div>

          <div>
            <label class="block text-xs font-bold text-surface-700 mb-1">
              {{ t('owner.descriptionLabel') }}
            </label>
            <textarea
              v-model="foodForm.description"
              rows="2"
              class="w-full py-2 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
            ></textarea>
          </div>

          <div>
            <label class="block text-xs font-bold text-surface-700 mb-1">
              {{ t('owner.imageUrlLabel') }}
            </label>
            <input
              v-model="foodForm.imageUrl"
              type="url"
              class="w-full py-2.5 px-3 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500"
            />
          </div>

          <div class="flex items-center justify-end gap-2 pt-4 border-t border-surface-100">
            <button
              type="button"
              class="px-4 py-2 rounded-xl border border-surface-200 text-surface-600 hover:bg-surface-50 text-xs font-semibold"
              @click="showFoodModal = false"
            >
              {{ t('owner.cancel') }}
            </button>
            <button
              type="submit"
              class="btn-primary !rounded-xl !py-2 !px-5 text-xs"
              :disabled="isSubmitting"
            >
              {{ isSubmitting ? '저장 중...' : t('owner.save') }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
