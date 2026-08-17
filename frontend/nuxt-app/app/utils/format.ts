/**
 * 금액 포맷팅 (locale 기반)
 */
export function formatPrice(price: number, locale: string = 'ko'): string {
  const currencyMap: Record<string, string> = {
    ko: 'KRW',
    ja: 'JPY',
  }
  const localeMap: Record<string, string> = {
    ko: 'ko-KR',
    ja: 'ja-JP',
  }
  return new Intl.NumberFormat(localeMap[locale] || 'ko-KR', {
    style: 'currency',
    currency: currencyMap[locale] || 'KRW',
  }).format(price)
}

/**
 * 날짜 포맷팅 (locale 기반)
 */
export function formatDate(dateString: string, locale: string = 'ko'): string {
  const localeMap: Record<string, string> = {
    ko: 'ko-KR',
    ja: 'ja-JP',
  }
  return new Date(dateString).toLocaleDateString(localeMap[locale] || 'ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * 주문 상태 라벨 반환
 *
 * i18n의 t() 함수를 받아서 처리합니다.
 * 사용 예: getOrderStatusLabel('PREPARING', t)
 *
 * @param status - 백엔드에서 전달받은 주문 상태 코드 (enum)
 * @param t - i18n의 t() 함수
 */
export function getOrderStatusLabel(
  status: string,
  t: (key: string) => string
): string {
  return t(`order.status.${status}`)
}
