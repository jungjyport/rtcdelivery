import type { $Fetch, FetchOptions } from 'ofetch'

/** 백엔드 공통 응답 래퍼 */
export interface ApiResponse<T> {
  status: number
  message: string
  data: T
}

export interface ApiRequestOptions extends Omit<FetchOptions, 'method' | 'baseURL'> {
  /** true면 Authorization 헤더를 주입하지 않는다. 기본값 false */
  skipAuth?: boolean
  /** true면 401이어도 토큰 갱신/재시도를 하지 않는다. 기본값 false */
  skipRefresh?: boolean
}

export interface ApiClient {
  get<T>(url: string, options?: ApiRequestOptions): Promise<T>
  post<T>(url: string, body?: unknown, options?: ApiRequestOptions): Promise<T>
  put<T>(url: string, body?: unknown, options?: ApiRequestOptions): Promise<T>
  patch<T>(url: string, body?: unknown, options?: ApiRequestOptions): Promise<T>
  delete<T>(url: string, options?: ApiRequestOptions): Promise<T>

  /** ApiResponse 래퍼 전체가 필요할 때 (page 메타 등) */
  raw<T>(url: string, options?: ApiRequestOptions & { method?: string }): Promise<ApiResponse<T>>

  /** useAsyncData / useFetch에 넘길 raw ofetch 인스턴스 */
  readonly fetcher: $Fetch
}

export class ApiError extends Error {
  /** HTTP 상태 코드. 네트워크 오류 등 응답이 없으면 0 */
  readonly status: number
  /** 백엔드 에러 코드(ApiResponse.message). 응답이 없으면 클라이언트 자체 코드 */
  readonly code: string
  /** 백엔드 응답 원본(ApiResponse.data). 검증 오류 상세 등 */
  readonly data: unknown
  readonly url: string
  readonly method: string

  constructor(opts: {
    status: number
    code: string
    url: string
    method: string
    data?: unknown
    message?: string
  }) {
    super(opts.message ?? opts.code)
    this.name = 'ApiError'
    this.status = opts.status
    this.code = opts.code
    this.data = opts.data ?? null
    this.url = opts.url
    this.method = opts.method
    Object.setPrototypeOf(this, new.target.prototype)
  }

  /** i18n 조회용 키. 예) 'error.RESTAURANT_NOT_FOUND' */
  get i18nKey(): string {
    return `error.${this.code}`
  }
}

declare module '#app' {
  interface NuxtApp {
    $api: ApiClient
  }
}

declare module 'vue' {
  interface ComponentCustomProperties {
    $api: ApiClient
  }
}
