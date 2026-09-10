import api from './axios'
import type { ApiEnvelope } from './auth'
import type { PageResponse } from './keys'

export interface AuditLogItem {
  id: number
  actor: string
  action: string
  target: string
  /** 복호화한 원문. detailDecrypted=false 면 암호화 이전 평문 행이거나 변조된 암호문 */
  detail: string
  detailDecrypted: boolean
  createdAt: string
}

export interface AuditViolation {
  fromId: number
  toId: number
  type: 'TAMPERED' | 'CHAIN_BROKEN'
}

/** 섀도(복사본) 비교 요약 — 목록(원본 내용)은 fetchForensics 로 */
export interface AuditShadowSummary {
  deleted: number
  inserted: number
  modified: number
  currentRows: number
  shadowRows: number
  shadowChainValid: boolean
  guard: 'ACTIVE' | 'DISABLED' | 'MISSING'
}

export interface AuditVerifyResult {
  /** 원본 체인만의 판정 */
  valid: boolean
  /** 체인 + 섀도 비교 + 보호 트리거를 합친 판정 (shadowChainValid 는 참고용) */
  healthy: boolean
  totalRows: number
  verifiedAt: string
  violations: AuditViolation[]
  shadow?: AuditShadowSummary
}

export interface AuditModifiedItem {
  id: number
  current: AuditLogItem
  original: AuditLogItem
  fields: string[]
}

/** 섀도 비교 상세 — 지워진 행(섀도 값)·끼어든 행·바뀐 행(원본 vs 현재). 각 목록 200건 상한 */
export interface AuditForensics {
  checkedAt: string
  chainValid: boolean
  shadowChainValid: boolean
  guard: 'ACTIVE' | 'DISABLED' | 'MISSING'
  currentRows: number
  shadowRows: number
  deletedCount: number
  insertedCount: number
  modifiedCount: number
  deleted: AuditLogItem[]
  inserted: AuditLogItem[]
  modified: AuditModifiedItem[]
}

/** 섀도 요약에 문제가 있는지 — 있을 때만 forensics 를 부른다 */
export function shadowHasIssue(r: AuditVerifyResult | null | 'unavailable'): boolean {
  if (!r || r === 'unavailable' || !r.shadow) return false
  const s = r.shadow
  return s.deleted + s.inserted + s.modified > 0 || s.guard !== 'ACTIVE'
}

export interface AuditListParams {
  actor?: string
  action?: string
  target?: string
  from?: string // "yyyy-MM-dd" (그날 00:00부터)
  to?: string // "yyyy-MM-dd" (그날 23:59까지)
  page?: number
  size?: number
  sort?: string
  direction?: 'asc' | 'desc'
}

/** 감사 대상 행위유형 — 백엔드 AuditHook 예약 목록과 동일 */
export const AUDIT_ACTIONS = [
  'LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGOUT',
  'KEY_CREATED', 'KEY_UPDATED', 'KEY_STATUS_CHANGED', 'KEY_ROTATED', 'KEY_REACTIVATED', 'KEY_DESTROYED',
  'KEY_INTEGRITY_VIOLATION', 'KEY_MATERIAL_VIEWED',
  'KEY_TEST_ENCRYPT', 'KEY_TEST_DECRYPT', 'KEY_TEST_SIGN', 'KEY_TEST_VERIFY',
  'USER_CREATED', 'USER_UPDATED', 'USER_PLAIN_VIEWED', 'USER_INTEGRITY_VIOLATION', 'USER_INTEGRITY_RESTORED',
  'AUDIT_CHAIN_VERIFIED', 'AUDIT_EXPORTED', 'AUDIT_CHAIN_VIOLATION', 'AUDIT_CHAIN_RESTORED',
  'AUDIT_SHADOW_BACKFILLED', 'AUDIT_SHADOW_GUARD_TAMPERED',
  'NOTICE_CREATED', 'NOTICE_UPDATED', 'NOTICE_DELETED', 'NOTICE_FILE_DOWNLOADED', 'NOTICE_FILE_DELETED',
] as const

export async function listAuditLogs(params: AuditListParams): Promise<PageResponse<AuditLogItem>> {
  const query: Record<string, string | number> = {}
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') query[k] = v
  }
  const res = await api.get<ApiEnvelope<PageResponse<AuditLogItem>>>('/api/audit-logs', { params: query })
  return res.data.data
}

/** 체인 상태 조회 — 감사 기록 없는 읽기 전용 검증 (감사 로그 화면 진입 시 자동 호출) */
/** 행위자 콤보박스 — 기록에 존재하는 행위자 목록 */
export async function fetchAuditActors(): Promise<string[]> {
  const res = await api.get<ApiEnvelope<string[]>>('/api/audit-logs/actors')
  return res.data.data
}

export async function fetchChainStatus(): Promise<AuditVerifyResult> {
  const res = await api.get<ApiEnvelope<AuditVerifyResult>>('/api/audit-logs/chain-status')
  return res.data.data
}

/** 섀도 비교 상세 — 읽기 전용, 위반이 있을 때만 호출 */
export async function fetchForensics(): Promise<AuditForensics> {
  const res = await api.get<ApiEnvelope<AuditForensics>>('/api/audit-logs/forensics')
  return res.data.data
}

/** 전체 해시 체인 재검증 — 검증 실행도 AUDIT_CHAIN_VERIFIED 로 기록되므로 POST */
export async function verifyAuditChain(): Promise<{ data: AuditVerifyResult; message: string | null }> {
  const res = await api.post<ApiEnvelope<AuditVerifyResult>>('/api/audit-logs/verify')
  return { data: res.data.data, message: res.data.message }
}

/** CSV 내려받기 — JWT 헤더가 필요하므로 blob 으로 받아 브라우저 저장을 트리거한다 */
export async function downloadAuditCsv(params: Omit<AuditListParams, 'page' | 'size'>): Promise<void> {
  const query: Record<string, string> = {}
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') query[k] = String(v)
  }
  const res = await api.get<Blob>('/api/audit-logs/export', { params: query, responseType: 'blob' })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = `audit_log_${new Date().toISOString().slice(0, 10).replaceAll('-', '')}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
