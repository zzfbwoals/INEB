import api from './axios'
import type { ApiEnvelope } from './auth'
import type { PageResponse } from './keys'

export interface AuditLogItem {
  id: number
  actor: string
  action: string
  target: string
  detail: string
  createdAt: string
}

export interface AuditViolation {
  fromId: number
  toId: number
  type: 'TAMPERED' | 'CHAIN_BROKEN'
}

export interface AuditVerifyResult {
  valid: boolean
  totalRows: number
  verifiedAt: string
  violations: AuditViolation[]
}

export interface AuditListParams {
  actor?: string
  action?: string
  target?: string
  from?: string // "yyyy-MM-dd" (그날 00:00부터)
  to?: string // "yyyy-MM-dd" (그날 23:59까지)
  page?: number
  size?: number
}

/** 감사 대상 행위유형 — 백엔드 AuditHook 예약 목록과 동일 */
export const AUDIT_ACTIONS = [
  'LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGOUT',
  'KEY_CREATED', 'KEY_UPDATED', 'KEY_STATUS_CHANGED', 'KEY_ROTATED', 'KEY_REACTIVATED', 'KEY_DESTROYED',
  'KEY_INTEGRITY_VIOLATION', 'KEY_MATERIAL_VIEWED',
  'KEY_TEST_ENCRYPT', 'KEY_TEST_DECRYPT', 'KEY_TEST_SIGN', 'KEY_TEST_VERIFY',
  'USER_CREATED', 'USER_UPDATED', 'USER_PLAIN_VIEWED',
  'AUDIT_CHAIN_VERIFIED', 'AUDIT_EXPORTED', 'AUDIT_CHAIN_VIOLATION', 'AUDIT_CHAIN_RESTORED',
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
export async function fetchChainStatus(): Promise<AuditVerifyResult> {
  const res = await api.get<ApiEnvelope<AuditVerifyResult>>('/api/audit-logs/chain-status')
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
