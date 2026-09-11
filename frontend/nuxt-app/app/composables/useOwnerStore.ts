import { ref } from 'vue'
import type { ApiError } from '~/types/api'
import type {
  RestaurantResponse,
  RestaurantCreateRequest,
  RestaurantUpdateRequest,
  FoodResponse,
  FoodCreateRequest,
  FoodUpdateRequest,
  PageResponse,
} from '~/types/catalog'

export function useOwnerStore() {
  const { $api } = useNuxtApp()
  const isSubmitting = ref(false)
  const errorMessage = ref<string | null>(null)

  /** 내 매장 목록 조회 */
  async function getMyRestaurants(page = 0, size = 50): Promise<PageResponse<RestaurantResponse>> {
    return await $api.get<PageResponse<RestaurantResponse>>('/restaurants', {
      query: { page, size, sort: 'id,desc' },
    })
  }

  /** 매장 단건 조회 */
  async function getRestaurant(id: number): Promise<RestaurantResponse> {
    const detail = await $api.get<{ restaurant: RestaurantResponse }>(`/restaurants/${id}`)
    return detail.restaurant
  }

  /** 신규 매장 등록 */
  async function createRestaurant(payload: RestaurantCreateRequest): Promise<RestaurantResponse> {
    isSubmitting.value = true
    errorMessage.value = null
    try {
      return await $api.post<RestaurantResponse>('/restaurants', payload)
    } catch (e) {
      const err = e as ApiError
      errorMessage.value = err.i18nKey
      throw err
    } finally {
      isSubmitting.value = false
    }
  }

  /** 매장 정보 수정 */
  async function updateRestaurant(id: number, payload: RestaurantUpdateRequest): Promise<RestaurantResponse> {
    isSubmitting.value = true
    errorMessage.value = null
    try {
      return await $api.patch<RestaurantResponse>(`/restaurants/${id}`, payload)
    } catch (e) {
      const err = e as ApiError
      errorMessage.value = err.i18nKey
      throw err
    } finally {
      isSubmitting.value = false
    }
  }

  /** 매장 비활성화 (삭제) */
  async function deleteRestaurant(id: number): Promise<void> {
    isSubmitting.value = true
    errorMessage.value = null
    try {
      await $api.delete<void>(`/restaurants/${id}`)
    } catch (e) {
      const err = e as ApiError
      errorMessage.value = err.i18nKey
      throw err
    } finally {
      isSubmitting.value = false
    }
  }

  /** 매장 소속 메뉴 목록 조회 */
  async function getRestaurantFoods(restaurantId: number): Promise<FoodResponse[]> {
    return await $api.get<FoodResponse[]>(`/restaurants/${restaurantId}/foods`)
  }

  /** 메뉴 등록 */
  async function createFood(restaurantId: number, payload: FoodCreateRequest): Promise<FoodResponse> {
    isSubmitting.value = true
    errorMessage.value = null
    try {
      return await $api.post<FoodResponse>(`/restaurants/${restaurantId}/foods`, payload)
    } catch (e) {
      const err = e as ApiError
      errorMessage.value = err.i18nKey
      throw err
    } finally {
      isSubmitting.value = false
    }
  }

  /** 메뉴 수정 */
  async function updateFood(restaurantId: number, foodId: number, payload: FoodUpdateRequest): Promise<FoodResponse> {
    isSubmitting.value = true
    errorMessage.value = null
    try {
      return await $api.patch<FoodResponse>(`/restaurants/${restaurantId}/foods/${foodId}`, payload)
    } catch (e) {
      const err = e as ApiError
      errorMessage.value = err.i18nKey
      throw err
    } finally {
      isSubmitting.value = false
    }
  }

  /** 메뉴 삭제 */
  async function deleteFood(restaurantId: number, foodId: number): Promise<void> {
    isSubmitting.value = true
    errorMessage.value = null
    try {
      await $api.delete<void>(`/restaurants/${restaurantId}/foods/${foodId}`)
    } catch (e) {
      const err = e as ApiError
      errorMessage.value = err.i18nKey
      throw err
    } finally {
      isSubmitting.value = false
    }
  }

  /** 메뉴 품절 원클릭 토글 */
  async function toggleFoodSoldOut(restaurantId: number, foodId: number, currentSoldOut: boolean): Promise<FoodResponse> {
    return await updateFood(restaurantId, foodId, { soldOut: !currentSoldOut })
  }

  return {
    isSubmitting,
    errorMessage,
    getMyRestaurants,
    getRestaurant,
    createRestaurant,
    updateRestaurant,
    deleteRestaurant,
    getRestaurantFoods,
    createFood,
    updateFood,
    deleteFood,
    toggleFoodSoldOut,
  }
}
