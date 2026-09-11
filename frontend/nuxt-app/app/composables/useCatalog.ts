import { toValue, type Ref } from 'vue'
import type {
  CategoryResponse,
  RestaurantResponse,
  RestaurantDetailResponse,
  FoodResponse,
  PageResponse,
} from '~/types/catalog'

export function useCatalog() {
  const { $api } = useNuxtApp()

  /** 활성 카테고리 목록 조회 (클라이언트) */
  async function getCategories(): Promise<CategoryResponse[]> {
    return await $api.get<CategoryResponse[]>('/categories')
  }

  /** 활성 카테고리 목록 SSR 프리페치 */
  function useCategoriesFetch() {
    return useApiFetch<CategoryResponse[]>('/categories')
  }

  /** 음식점 목록 페이징/검색/필터 SSR 프리페치 */
  function useRestaurantsFetch(params: Ref<Record<string, any>>) {
    return useApiFetch<PageResponse<RestaurantResponse>>('/restaurants', {
      query: params,
      watch: [params],
    })
  }

  /** 특정 음식점 상세 및 메뉴 목록 SSR 프리페치 */
  function useRestaurantDetailFetch(restaurantId: Ref<number | string> | number | string) {
    return useApiFetch<RestaurantDetailResponse>(
      () => `/restaurants/${toValue(restaurantId)}`,
    )
  }

  /** 음식점 가로지르는 메뉴 키워드 검색 */
  async function searchFoods(keyword: string, page = 0, size = 10): Promise<PageResponse<FoodResponse>> {
    return await $api.get<PageResponse<FoodResponse>>('/foods', {
      query: { keyword, page, size },
    })
  }

  return {
    getCategories,
    useCategoriesFetch,
    useRestaurantsFetch,
    useRestaurantDetailFetch,
    searchFoods,
  }
}
