import { defineStore } from 'pinia'

export interface CartItem {
  foodId: number
  name: string
  price: number
  quantity: number
  imageUrl?: string
  restaurantId: number
  restaurantName: string
}

interface CartState {
  items: CartItem[]
  restaurantId: number | null
  restaurantName: string | null
}

export const useCartStore = defineStore('cart', {
  state: (): CartState => ({
    items: [],
    restaurantId: null,
    restaurantName: null,
  }),

  getters: {
    totalItems: (state) => state.items.reduce((sum, item) => sum + item.quantity, 0),
    totalPrice: (state) => state.items.reduce((sum, item) => sum + item.price * item.quantity, 0),
    isEmpty: (state) => state.items.length === 0,
  },

  actions: {
    addItem(item: CartItem) {
      // 다른 레스토랑 메뉴가 이미 있으면 초기화
      if (this.restaurantId && this.restaurantId !== item.restaurantId) {
        this.clearCart()
      }

      this.restaurantId = item.restaurantId
      this.restaurantName = item.restaurantName

      const existing = this.items.find((i) => i.foodId === item.foodId)
      if (existing) {
        existing.quantity += item.quantity
      } else {
        this.items.push({ ...item })
      }
    },

    removeItem(foodId: number) {
      this.items = this.items.filter((item) => item.foodId !== foodId)
      if (this.items.length === 0) {
        this.restaurantId = null
        this.restaurantName = null
      }
    },

    updateQuantity(foodId: number, quantity: number) {
      const item = this.items.find((i) => i.foodId === foodId)
      if (item) {
        item.quantity = quantity
        if (item.quantity <= 0) {
          this.removeItem(foodId)
        }
      }
    },

    clearCart() {
      this.items = []
      this.restaurantId = null
      this.restaurantName = null
    },
  },
})
