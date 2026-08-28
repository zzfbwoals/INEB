/* 시각·기간 표기 — 백엔드가 KST "yyyy-MM-dd HH:mm:ss" 문자열을 주므로 변환 없이 자른다 */

/** "yyyy-MM-dd HH:mm:ss" → "yyyy-MM-dd HH:mm". 빈 값은 대시 */
export function fmt(value: string | null | undefined, empty = '—'): string {
  if (!value) return empty
  return value.slice(0, 16)
}

/** "yyyy-MM-dd HH:mm:ss" → "yyyy-MM-dd" */
export function fmtDate(value: string | null | undefined, empty = '—'): string {
  if (!value) return empty
  return value.slice(0, 10)
}

/** datetime-local 입력값(YYYY-MM-DDTHH:mm) → 백엔드 요청 포맷(yyyy-MM-dd HH:mm:00) */
export function fromDateTimeLocal(value: string): string | null {
  if (!value) return null
  return value.replace('T', ' ') + (value.length === 16 ? ':00' : '')
}

/** 백엔드 포맷 → datetime-local 입력값 */
export function toDateTimeLocal(value: string | null | undefined): string {
  if (!value) return ''
  return value.slice(0, 16).replace(' ', 'T')
}

/** 입력한 활성일이 미래인지 (비어 있으면 false = 즉시 활성) */
export function isFuture(dateTimeLocal: string): boolean {
  if (!dateTimeLocal) return false
  return new Date(dateTimeLocal).getTime() > Date.now()
}

/** KST 문자열 기준 D-day (양수: 남음, 0 이하: 지남). 빈 값은 null */
export function dday(value: string | null | undefined): number | null {
  if (!value) return null
  const target = new Date(value.replace(' ', 'T') + '+09:00').getTime()
  return Math.ceil((target - Date.now()) / 86_400_000)
}

/** 오늘 + n일 → "yyyy-MM-dd" (미리보기용) */
export function plusDays(days: number): string {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}
