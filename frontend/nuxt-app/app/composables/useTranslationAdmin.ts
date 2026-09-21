import { ref } from 'vue'
import type { ApiError } from '~/types/api'

export interface TranslationJobAdminItem {
  id: number
  eventId: string
  restaurantId: number
  sourceLocale: string
  targetLocale: string
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'SUPERSEDED'
  retryCount: number
  nextRetryAt: string
  lastError: string | null
  entries: string
  createdAt: string
  updatedAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first?: boolean
  last?: boolean
}

export function useTranslationAdmin() {
  const { $api } = useNuxtApp()
  const jobs = ref<TranslationJobAdminItem[]>([])
  const totalElements = ref(0)
  const totalPages = ref(0)
  const currentPage = ref(0)
  const isLoading = ref(false)
  const isActionLoading = ref(false)
  const errorMessage = ref<string | null>(null)
  const actionMessage = ref<string | null>(null)

  async function fetchJobs(status?: string, page = 0, size = 20) {
    isLoading.value = true
    errorMessage.value = null
    try {
      const query: Record<string, string | number> = {
        page,
        size,
      }
      if (status && status !== 'ALL') {
        query.status = status
      }
      const res = await $api.get<PageResponse<TranslationJobAdminItem>>('/translations/admin/jobs', {
        query,
      })
      jobs.value = res.content || []
      totalElements.value = res.totalElements || 0
      totalPages.value = res.totalPages || 0
      currentPage.value = res.number || 0
    } catch (e) {
      const err = e as ApiError
      errorMessage.value = err.message || 'Failed to fetch translation jobs'
    } finally {
      isLoading.value = false
    }
  }

  async function retryJob(jobId: number): Promise<boolean> {
    isActionLoading.value = true
    actionMessage.value = null
    errorMessage.value = null
    try {
      await $api.post(`/translations/admin/jobs/${jobId}/retry`)
      actionMessage.value = `Job #${jobId} marked for retry.`
      return true
    } catch (e) {
      const err = e as ApiError
      errorMessage.value = err.message || `Failed to retry job #${jobId}`
      return false
    } finally {
      isActionLoading.value = false
    }
  }

  async function retryAllFailedJobs(): Promise<number> {
    isActionLoading.value = true
    actionMessage.value = null
    errorMessage.value = null
    try {
      const res = await $api.post<{ retriedCount: number }>('/translations/admin/jobs/retry-all-failed')
      actionMessage.value = `${res.retriedCount} failed jobs marked for retry.`
      return res.retriedCount
    } catch (e) {
      const err = e as ApiError
      errorMessage.value = err.message || 'Failed to retry all failed jobs'
      return 0
    } finally {
      isActionLoading.value = false
    }
  }

  return {
    jobs,
    totalElements,
    totalPages,
    currentPage,
    isLoading,
    isActionLoading,
    errorMessage,
    actionMessage,
    fetchJobs,
    retryJob,
    retryAllFailedJobs,
  }
}
