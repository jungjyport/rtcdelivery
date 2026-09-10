// ============================================================
// API 관련 타입 정의
// ============================================================

import type { AuthUser } from '~/stores/authStore'

export type { ApiResponse } from './api'

/** 페이지네이션 응답 */
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

// ============================================================
// 회원 관련 타입
// ============================================================

export type { AuthUser, AuthProvider } from '~/stores/authStore'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResult {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: AuthUser
}

export interface SignupRequest {
  username: string
  password: string
  nickname: string
  email?: string
}

export interface MemberProfile {
  id: number
  username: string
  nickname: string
  email?: string
  role: string
  authProvider: import('~/stores/authStore').AuthProvider
  createdAt: string
}

// ============================================================
// 음식 / 카탈로그 관련 타입
// ============================================================

export interface Restaurant {
  id: number
  name: string
  description: string
  address: string
  phone: string
  imageUrl: string
  rating: number
  deliveryFee: number
  minOrderAmount: number
  categoryId: number
  categoryName: string
  isOpen: boolean
}

export interface Food {
  id: number
  name: string
  description: string
  price: number
  imageUrl: string
  restaurantId: number
  restaurantName: string
  categoryId: number
  isAvailable: boolean
}

export interface Category {
  id: number
  name: string
  iconUrl: string
  sortOrder: number
}

// ============================================================
// 주문 관련 타입
// ============================================================

export interface Order {
  id: number
  memberId: number
  restaurantId: number
  restaurantName: string
  orderItems: OrderItem[]
  totalPrice: number
  deliveryFee: number
  status: OrderStatus
  deliveryAddress: string
  createdAt: string
}

export interface OrderItem {
  foodId: number
  foodName: string
  price: number
  quantity: number
}

export type OrderStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'PREPARING'
  | 'READY'
  | 'DELIVERING'
  | 'DELIVERED'
  | 'CANCELLED'

// ============================================================
// 결제 관련 타입
// ============================================================

export interface Payment {
  id: number
  orderId: number
  amount: number
  method: PaymentMethod
  status: PaymentStatus
  createdAt: string
}

export type PaymentMethod = 'CARD' | 'BANK_TRANSFER' | 'KAKAO_PAY' | 'NAVER_PAY' | 'TOSS_PAY'
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED'
