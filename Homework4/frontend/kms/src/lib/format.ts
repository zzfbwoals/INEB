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

/** KST 문자열 → "3일 전" 상대 시간 (정확 시각은 title 로 병기) */
export function relTime(value: string | null | undefined): string {
  if (!value) return '—'
  const diff = Date.now() - new Date(value.replace(' ', 'T') + '+09:00').getTime()
  const m = Math.floor(diff / 60_000)
  if (m < 1) return '방금 전'
  if (m < 60) return `${m}분 전`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}시간 전`
  const d = Math.floor(h / 24)
  if (d < 30) return `${d}일 전`
  const mo = Math.floor(d / 30)
  if (mo < 12) return `${mo}개월 전`
  return `${Math.floor(mo / 12)}년 전`
}

/** 최근 7일 내 사용 여부 — 위험 작업(정지·삭제) 경고 기준 */
export function within7Days(value: string | null | undefined): boolean {
  if (!value) return false
  return Date.now() - new Date(value.replace(' ', 'T') + '+09:00').getTime() < 7 * 86_400_000
}

/** 문자열을 파일로 저장 (공개키 .pem 등) — 값이 이미 프론트에 있어 서버 왕복 없음 */
export function downloadText(name: string, content: string, type = 'text/plain') {
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([content], { type }))
  a.download = name
  a.click()
  URL.revokeObjectURL(a.href)
}

/** 오늘 + n일 → "yyyy-MM-dd" (미리보기용) */
export function plusDays(days: number): string {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}
