import { computed, toValue, isRef, type Ref } from 'vue'
import type { AsyncData, AsyncDataOptions } from '#app'
import type { ApiClient, ApiRequestOptions } from '~/types/api'
import { ApiError } from '~/types/api'

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export type UseApiFetchOptions<T> = ApiRequestOptions & AsyncDataOptions<T> & {
  method?: HttpMethod
  key?: string | (() => string)
}

export function useApiFetch<T>(
  url: string | (() => string),
  options: UseApiFetchOptions<T> = {},
): AsyncData<T, ApiError> {
  const { $api } = useNuxtApp()
  const { locale } = useI18n()

  const {
    server,
    lazy,
    immediate,
    default: defaultFn,
    transform,
    pick,
    watch,
    deep,
    dedupe,
    getCachedData,
    enabled,
    method = 'GET',
    key: customKey,
    ...requestOptions
  } = options

  // query가 ref인 경우 () => string 팩토리로 key를 동적 계산 (query 변경 시 캐시 히트 방지)
  const resolvedKey: string | (() => string) = customKey
    ?? (isRef(requestOptions.query)
      ? () => buildApiFetchKey(url, (requestOptions.query as Ref<unknown>).value)
      : buildApiFetchKey(url, requestOptions.query))

  const querySource = isRef(requestOptions.query) ? [requestOptions.query] : []
  const watchSources = [
    locale,
    ...querySource,
    ...(Array.isArray(watch) ? watch : watch ? [watch] : []),
  ]

  const asyncData = useAsyncData<T, ApiError>(
    resolvedKey,
    () => {
      const path = typeof url === 'function' ? url() : url
      return callApi<T>($api, path, method, requestOptions)
    },
    {
      server,
      lazy,
      immediate,
      default: defaultFn,
      transform,
      pick,
      watch: watchSources,
      deep,
      dedupe,
      getCachedData,
      enabled,
      timeout: requestOptions.timeout,
    },
  )

  const error = computed({
    get: () => unwrapApiError(asyncData.error.value) ?? undefined,
    set: (value) => {
      asyncData.error.value = value
    },
  })

  return Object.assign(asyncData, { error }) as AsyncData<T, ApiError>
}

function unwrapApiError(error: unknown): ApiError | undefined {
  if (!error) return undefined
  if (error instanceof ApiError) return error
  if (typeof error === 'object' && 'cause' in error && error.cause instanceof ApiError) {
    return error.cause
  }
  const status = typeof error === 'object' && error
    ? Number((error as { statusCode?: number, status?: number }).statusCode
      ?? (error as { status?: number }).status
      ?? 0)
    : 0
  return new ApiError({
    status,
    code: 'UNKNOWN_ERROR',
    url: '',
    method: 'GET',
    data: error,
    message: error instanceof Error ? error.message : undefined,
  })
}

function unwrapValue(val: unknown): any {
  const unwrapped = toValue(val)
  if (unwrapped !== null && typeof unwrapped === 'object' && !Array.isArray(unwrapped)) {
    const res: Record<string, any> = {}
    for (const [k, v] of Object.entries(unwrapped)) {
      const inner = toValue(v)
      if (inner !== undefined) {
        res[k] = inner
      }
    }
    return res
  }
  return unwrapped
}

function buildApiFetchKey(url: string | (() => string), query: unknown): string {
  const path = typeof url === 'function' ? url() : url
  const safeQuery = unwrapValue(query)
  if (!safeQuery || (typeof safeQuery === 'object' && Object.keys(safeQuery).length === 0)) {
    return `api:${path}`
  }
  try {
    return `api:${path}:${JSON.stringify(safeQuery)}`
  } catch {
    return `api:${path}`
  }
}

function callApi<T>(
  api: ApiClient,
  path: string,
  method: HttpMethod,
  options: ApiRequestOptions,
) {
  const safeOptions: ApiRequestOptions = {
    ...options,
    query: unwrapValue(options.query),
    body: unwrapValue(options.body),
  }
  switch (method) {
    case 'POST':
      return api.post<T>(path, safeOptions.body, safeOptions)
    case 'PUT':
      return api.put<T>(path, safeOptions.body, safeOptions)
    case 'PATCH':
      return api.patch<T>(path, safeOptions.body, safeOptions)
    case 'DELETE':
      return api.delete<T>(path, safeOptions)
    default:
      return api.get<T>(path, safeOptions)
  }
}
