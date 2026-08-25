import { $fetch, FetchError } from 'ofetch'
import type { FetchOptions, FetchResponse } from 'ofetch'
import type { ApiClient, ApiRequestOptions, ApiResponse } from '~/types/api'
import { ApiError } from '~/types/api'

export interface ApiClientDeps {
  baseURL: string
  getAccessToken: () => string | null
  setAccessToken: (token: string) => void
  getLocale: () => string
  onLogout: () => void | Promise<void>
  isServer: boolean
}

const AUTH_EXEMPT_PATHS = ['/auth/login', '/auth/signup', '/auth/refresh-token'] as const

type InternalFetchOptions = FetchOptions<'json'> & { skipAuth?: boolean }

/** 공개 API의 body는 unknown이고, ofetch FetchOptions.body는 그보다 좁다. */
type ExecuteOptions = Omit<ApiRequestOptions, 'body'> & {
  method?: string
  body?: unknown
}

export function createApiClient(deps: ApiClientDeps): ApiClient {
  let refreshPromise: Promise<string> | null = null
  let isLoggingOut = false

  const fetcher = $fetch.create({
    baseURL: deps.baseURL,
    credentials: 'include',
    timeout: 10000,
    retry: false,
    headers: {
      'Content-Type': 'application/json',
    },
    onRequest({ options }) {
      const req = options as InternalFetchOptions

      if (req.body instanceof FormData) {
        options.headers.delete('Content-Type')
      }

      if (!req.skipAuth && !deps.isServer) {
        const token = deps.getAccessToken()
        if (token) {
          options.headers.set('Authorization', `Bearer ${token}`)
        }
      }

      if (!options.headers.has('Accept-Language')) {
        options.headers.set('Accept-Language', deps.getLocale())
      }
    },
  })

  const handleLogout = async () => {
    if (isLoggingOut) return
    isLoggingOut = true
    try {
      await deps.onLogout()
    }
    finally {
      isLoggingOut = false
    }
  }

  const ensureRefreshed = (): Promise<string> => {
    if (refreshPromise) return refreshPromise

    const pending = (async () => {
      try {
        const data = await execute<{ accessToken: string }>(
          '/auth/refresh-token',
          { method: 'POST', skipAuth: true, skipRefresh: true },
          false,
          true,
        )
        deps.setAccessToken(data.accessToken)
        return data.accessToken
      }
      catch (error) {
        refreshPromise = null
        await handleLogout()
        throw error
      }
    })()

    refreshPromise = pending
    void pending.finally(() => {
      if (refreshPromise === pending) refreshPromise = null
    })
    return pending
  }

  async function execute<T>(
    url: string,
    options: ExecuteOptions,
    retried: boolean,
    unwrap: true,
  ): Promise<T>
  async function execute<T>(
    url: string,
    options: ExecuteOptions,
    retried: boolean,
    unwrap: false,
  ): Promise<ApiResponse<T>>
  async function execute<T>(
    url: string,
    options: ExecuteOptions,
    retried: boolean,
    unwrap: boolean,
  ): Promise<T | ApiResponse<T>> {
    const method = options.method ?? 'GET'
    const skipRefresh = options.skipRefresh === true

    try {
      return await send<T>(url, options, unwrap)
    }
    catch (error) {
      const apiError = toApiError(error, url, method)
      const canRefresh =
        apiError.status === 401
        && !skipRefresh
        && !retried
        && !deps.isServer
        && !isAuthExemptPath(url)

      if (!canRefresh) throw apiError

      await ensureRefreshed()
      return unwrap
        ? execute<T>(url, options, true, true)
        : execute<T>(url, options, true, false)
    }
  }

  async function send<T>(
    url: string,
    options: ExecuteOptions,
    unwrap: boolean,
  ): Promise<T | ApiResponse<T>> {
    const { skipAuth = false, skipRefresh: _skipRefresh, method = 'GET', body, ...fetchOptions } = options

    let response: FetchResponse<unknown>
    try {
      response = await fetcher.raw<unknown>(url, {
        ...fetchOptions,
        method,
        skipAuth,
        body: body as FetchOptions['body'],
      } as InternalFetchOptions)
    }
    catch (error) {
      throw toApiError(error, url, String(method))
    }

    if (response.status === 204) {
      if (unwrap) return undefined as T
      return { status: 204, message: 'Success', data: undefined as T }
    }

    const payload = response._data
    if (!isApiResponse(payload)) {
      throw new ApiError({
        status: response.status,
        code: 'INVALID_RESPONSE',
        data: payload,
        url,
        method: String(method),
      })
    }

    return unwrap ? (payload.data as T) : (payload as ApiResponse<T>)
  }

  return {
    get<T>(url: string, options?: ApiRequestOptions) {
      return execute<T>(url, { ...options, method: 'GET' }, false, true)
    },
    post<T>(url: string, body?: unknown, options?: ApiRequestOptions) {
      return execute<T>(url, { ...options, method: 'POST', body }, false, true)
    },
    put<T>(url: string, body?: unknown, options?: ApiRequestOptions) {
      return execute<T>(url, { ...options, method: 'PUT', body }, false, true)
    },
    patch<T>(url: string, body?: unknown, options?: ApiRequestOptions) {
      return execute<T>(url, { ...options, method: 'PATCH', body }, false, true)
    },
    delete<T>(url: string, options?: ApiRequestOptions) {
      return execute<T>(url, { ...options, method: 'DELETE' }, false, true)
    },
    raw<T>(url: string, options?: ApiRequestOptions & { method?: string }) {
      return execute<T>(url, { method: 'GET', ...options }, false, false)
    },
    fetcher,
  }
}

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  if (value === null || typeof value !== 'object') return false
  const record = value as Record<string, unknown>
  return typeof record.status === 'number'
    && typeof record.message === 'string'
    && 'data' in record
}

function isAuthExemptPath(url: string): boolean {
  const path = extractPath(url)
  return AUTH_EXEMPT_PATHS.some(exempt => path === exempt || path.endsWith(exempt))
}

function extractPath(url: string): string {
  try {
    const pathname = url.includes('://')
      ? new URL(url).pathname
      : (url.split('?')[0] ?? url)
    return pathname.replace(/\/+$/, '') || '/'
  }
  catch {
    return url
  }
}

function isTimeoutError(error: FetchError): boolean {
  const causeMessage = error.cause instanceof Error ? error.cause.message : ''
  const combined = `${error.name} ${error.message} ${causeMessage}`
  return /timeout/i.test(combined)
}

function toApiError(error: unknown, url: string, method: string): ApiError {
  if (error instanceof ApiError) return error

  if (error instanceof FetchError) {
    if (isTimeoutError(error)) {
      return new ApiError({
        status: 0,
        code: 'REQUEST_TIMEOUT',
        url,
        method,
      })
    }

    const status = error.statusCode ?? error.status ?? 0
    if (!error.response && status === 0) {
      return new ApiError({
        status: 0,
        code: 'NETWORK_ERROR',
        url,
        method,
      })
    }

    const payload = error.data
    if (isApiResponse(payload)) {
      return new ApiError({
        status: status || payload.status,
        code: payload.message || 'UNKNOWN_ERROR',
        data: payload.data,
        url,
        method,
      })
    }

    return new ApiError({
      status,
      code: 'UNKNOWN_ERROR',
      data: payload,
      url,
      method,
    })
  }

  return new ApiError({
    status: 0,
    code: 'NETWORK_ERROR',
    data: error,
    url,
    method,
  })
}
