import axios from 'axios'
import type { AxiosInstance } from 'axios'

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig()

  const api: AxiosInstance = axios.create({
    baseURL: config.public.apiBaseUrl as string,
    timeout: 10000,
    headers: {
      'Content-Type': 'application/json',
    },
  })

  // Request Interceptor - JWT 토큰 자동 첨부
  api.interceptors.request.use(
    (config) => {
      const token = localStorage.getItem('accessToken')
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
      return config
    },
    (error) => Promise.reject(error)
  )

  // Response Interceptor - 에러 핸들링
  api.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error.response?.status === 401) {
        localStorage.removeItem('accessToken')
        navigateTo('/auth/login')
      }
      return Promise.reject(error)
    }
  )

  return {
    provide: {
      api,
    },
  }
})
