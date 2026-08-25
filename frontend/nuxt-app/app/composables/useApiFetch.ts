import type { AsyncData, AsyncDataOptions } from '#app'
import type { ApiClient, ApiRequestOptions } from '~/types/api'
import { ApiError } from '~/types/api'

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export type UseApiFetchOptions<T> = ApiRequestOptions & AsyncDataOptions<T> & {
  method?: HttpMethod
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
    ...requestOptions
  } = options

  const resolvedKey = buildApiFetchKey(url, requestOptions.query)
  const watchSources = [
    locale,
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

function buildApiFetchKey(url: string | (() => string), query: unknown): string {
  const path = typeof url === 'function' ? url() : url
  return `api:${path}:${query ? JSON.stringify(query) : ''}`
}

function callApi<T>(
  api: ApiClient,
  path: string,
  method: HttpMethod,
  options: ApiRequestOptions,
) {
  switch (method) {
    case 'POST':
      return api.post<T>(path, options.body, options)
    case 'PUT':
      return api.put<T>(path, options.body, options)
    case 'PATCH':
      return api.patch<T>(path, options.body, options)
    case 'DELETE':
      return api.delete<T>(path, options)
    default:
      return api.get<T>(path, options)
  }
}
