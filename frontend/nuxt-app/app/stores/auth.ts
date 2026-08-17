import { defineStore } from 'pinia'

interface AuthState {
  user: User | null
  accessToken: string | null
  isAuthenticated: boolean
}

interface User {
  id: number
  email: string
  name: string
  phone: string
  role: 'USER' | 'OWNER' | 'RIDER' | 'ADMIN'
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    accessToken: null,
    isAuthenticated: false,
  }),

  getters: {
    getUser: (state) => state.user,
    getToken: (state) => state.accessToken,
    isLoggedIn: (state) => state.isAuthenticated,
  },

  actions: {
    setAuth(user: User, token: string) {
      this.user = user
      this.accessToken = token
      this.isAuthenticated = true
      localStorage.setItem('accessToken', token)
    },

    logout() {
      this.user = null
      this.accessToken = null
      this.isAuthenticated = false
      localStorage.removeItem('accessToken')
    },

    initAuth() {
      const token = localStorage.getItem('accessToken')
      if (token) {
        this.accessToken = token
        this.isAuthenticated = true
        // TODO: /api/v1/members/me 호출하여 유저 정보 로드
      }
    },
  },
})
