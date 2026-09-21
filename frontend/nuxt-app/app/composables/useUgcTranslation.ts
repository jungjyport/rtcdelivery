import { ref } from 'vue'
import type { ApiError } from '~/types/api'

export type UgcContentType = 'REVIEW' | 'INQUIRY' | 'COMMENT'

export interface UgcTranslationResponse {
  contentType: UgcContentType
  contentId: number
  sourceLocale: string
  targetLocale: string
  translatedText: string
  cached: boolean
}

export const useUgcTranslation = () => {
  const { $api } = useNuxtApp()
  const { t, locale } = useI18n()

  // 캐시 키에 locale을 포함. 언어 변경 시 이전 언어의 번역이 노출되는 것을 방지.
  const cache = new Map<string, string>()
  const pending = ref<Set<string>>(new Set())
  const errorMessage = ref('')

  const keyOf = (type: UgcContentType, id: number) => `${type}:${id}:${locale.value}`

  const translate = async (type: UgcContentType, id: number, text: string): Promise<string | null> => {
    const key = keyOf(type, id)
    const hit = cache.get(key)
    if (hit) return hit

    pending.value.add(key)
    errorMessage.value = ''
    try {
      const res = await $api.post<UgcTranslationResponse>('/translations/ugc', {
        contentType: type,
        contentId: id,
        text,
        sourceLocale: 'ko',
      })
      cache.set(key, res.translatedText)
      return res.translatedText
    } catch (e) {
      const err = e as ApiError
      errorMessage.value = err?.i18nKey ? t(err.i18nKey) : t('error.UNKNOWN_ERROR')
      return null
    } finally {
      pending.value.delete(key)
    }
  }

  return { translate, pending, errorMessage }
}
