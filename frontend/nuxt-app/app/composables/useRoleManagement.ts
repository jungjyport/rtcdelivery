import { ref } from 'vue'
import type { ApiError } from '~/types/api'
import type { MemberResponse } from '~/types/catalog'

export function useRoleManagement() {
  const { $api } = useNuxtApp()
  const isSubmitting = ref(false)
  const errorMessage = ref<string | null>(null)
  const successResult = ref<MemberResponse | null>(null)

  async function updateMemberRole(memberId: number, role: string): Promise<MemberResponse> {
    isSubmitting.value = true
    errorMessage.value = null
    successResult.value = null

    try {
      const res = await $api.patch<MemberResponse>(`/members/${memberId}/role`, { role })
      successResult.value = res
      return res
    } catch (e) {
      const err = e as ApiError
      errorMessage.value = err.i18nKey
      throw err
    } finally {
      isSubmitting.value = false
    }
  }

  return {
    isSubmitting,
    errorMessage,
    successResult,
    updateMemberRole,
  }
}
