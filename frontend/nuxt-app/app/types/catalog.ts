export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface CategoryResponse {
  id: number
  code: string
  name: string
  imageUrl: string | null
  displayOrder: number
}

export interface RestaurantResponse {
  id: number
  categoryId: number
  categoryCode: string
  name: string
  description: string | null
  address: string
  phoneNumber: string | null
  deliveryFee: number
  minOrderAmount: number
  imageUrl: string | null
  locale: string
}

export interface FoodResponse {
  id: number
  restaurantId: number
  name: string
  description: string | null
  price: number
  imageUrl: string | null
  soldOut: boolean
  displayOrder: number
  locale: string
}

export interface RestaurantDetailResponse {
  restaurant: RestaurantResponse
  foods: FoodResponse[]
}

export interface RestaurantCreateRequest {
  categoryId: number
  name: string
  description?: string
  address: string
  phoneNumber?: string
  deliveryFee: number
  minOrderAmount: number
  imageUrl?: string
}

export interface RestaurantUpdateRequest {
  categoryId?: number
  name?: string
  description?: string
  address?: string
  phoneNumber?: string
  deliveryFee?: number
  minOrderAmount?: number
  imageUrl?: string
}

export interface FoodCreateRequest {
  name: string
  description?: string
  price: number
  imageUrl?: string
  displayOrder?: number
}

export interface FoodUpdateRequest {
  name?: string
  description?: string
  price?: number
  imageUrl?: string
  soldOut?: boolean
  displayOrder?: number
}

export interface MemberResponse {
  id: number
  username: string
  nickname: string
  email: string | null
  role: string
  authProvider: string
  createdAt: string
}

export interface RoleUpdateRequest {
  role: string
}
