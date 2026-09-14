import api from './axios'
import type { ApiEnvelope } from './auth'
import type { PageResponse } from './keys'

export interface AuditLogItem {
  /** 위반 상세(diff)용 해시 — 화면은 열 너비만큼 보이고 넘치면 … (CSS 말줄임) */
  prevHash?: string
  rowHash?: string
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
}

export interface AuditVerifyResult {
  /** 원본 체인만의 판정 */
  valid: boolean
  /** checksOk && !flagged — 초록 (shadowChainValid 는 참고용) */
  healthy: boolean
  /** 관리자가 아직 확인하지 않은 변조 증거가 있는지 — 빨강. 증거는 원복해도 남으므로 확인 전까지 유지 */
  flagged: boolean
  /** 지금 검사(체인 + 섀도 비교) 통과 — 전부 확인했는데 false 면 주황(원복 필요) */
  checksOk: boolean
  /** 미확인 증거 행 수 */
  unacknowledgedRows: number
  /** 변조 증거(audit_violation)에 남은 행 수 — 원복해도 유지, 배지 "체인 위반 N건" */
  flaggedRows: number
  totalRows: number
  verifiedAt: string
  violations: AuditViolation[]
  shadow?: AuditShadowSummary
}

/** 확인(acknowledge) 기록 — 누가·언제·왜 (AUDIT_VIOLATION_ACKNOWLEDGED, 원복 없음) */
export interface AuditAck {
  by: string
  at: string
  reason: string
}

/** 체인만 깨진 구간(섀도 차이 없음) — id(fromId)~toId, current 는 fromId 행의 현재 값 */
export interface AuditChainItem {
  id: number
  toId: number
  current: AuditLogItem
}

/** 증거 행 하나 + 확인 기록. 감사 행(auditId)의 증거가 전부 확인돼야 그 행이 "확인됨" */
export interface AuditEvidenceItem {
  id: number
  auditId: number
  kind: 'MODIFIED' | 'INSERTED' | 'DELETED' | 'CHAIN'
  fields: string
  ack: AuditAck | null
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
  currentRows: number
  shadowRows: number
  deletedCount: number
  insertedCount: number
  modifiedCount: number
  chainCount: number
  unacknowledgedRows: number
  deleted: AuditLogItem[]
  inserted: AuditLogItem[]
  modified: AuditModifiedItem[]
  chain: AuditChainItem[]
  evidence: AuditEvidenceItem[]
}

/** 제목 옆 색상점·대시보드 방패 색 — 미확인 증거 있음 = bad(빨강) / 전부 확인했지만 검사 실패 = ack(주황) / 정상 = ok(초록) */
export type ChainState = 'loading' | 'na' | 'ok' | 'ack' | 'bad'

export function chainState(r: AuditVerifyResult | null | 'unavailable'): ChainState {
  if (r === null) return 'loading'
  if (r === 'unavailable') return 'na'
  if (r.flagged) return 'bad'
  if (!r.checksOk) return 'ack'
  return 'ok'
}

export function chainTip(r: AuditVerifyResult | null | 'unavailable'): string {
  switch (chainState(r)) {
    case 'loading': return '확인 중'
    case 'na': return '체인 확인 불가'
    case 'ok': return '체인 정상'
    case 'ack': return '위반 확인 완료 · 원복 필요'
    default: {
      const v = r as AuditVerifyResult
      return `체인 위반 ${Math.max(v.flaggedRows, v.violations.length)}건 · 미확인 ${v.unacknowledgedRows}건`
    }
  }
}

/** 섀도 요약에 문제가 있는지 — 있을 때만 forensics 를 부른다 */
export function shadowHasIssue(r: AuditVerifyResult | null | 'unavailable'): boolean {
  if (!r || r === 'unavailable' || !r.shadow) return false
  const s = r.shadow
  return s.deleted + s.inserted + s.modified > 0
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

/** 무결성 위반 행위(KEY_/USER_INTEGRITY_VIOLATION) — 감사 로그 표·상세의 행위 칩을 빨갛게 */
export function isIntegrityViolation(action: string): boolean {
  return /_INTEGRITY_VIOLATION$/.test(action)
}

/** 감사 대상 행위유형 — 백엔드 AuditHook 예약 목록과 동일 */
export const AUDIT_ACTIONS = [
  'LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGOUT',
  'KEY_CREATED', 'KEY_UPDATED', 'KEY_STATUS_CHANGED', 'KEY_ROTATED', 'KEY_REACTIVATED', 'KEY_DESTROYED',
  'KEY_INTEGRITY_VIOLATION', 'KEY_INTEGRITY_RESEALED', 'KEY_MATERIAL_VIEWED',
  'KEY_TEST_ENCRYPT', 'KEY_TEST_DECRYPT', 'KEY_TEST_SIGN', 'KEY_TEST_VERIFY',
  'USER_CREATED', 'USER_UPDATED', 'USER_PLAIN_VIEWED', 'USER_INTEGRITY_VIOLATION', 'USER_INTEGRITY_RESEALED',
  'AUDIT_CHAIN_VERIFIED', 'AUDIT_EXPORTED', 'AUDIT_CHAIN_VIOLATION', 'AUDIT_CHAIN_RESTORED', 'AUDIT_VIOLATION_ACKNOWLEDGED',
  'DB_DIRECT_CHANGE', 'INTEGRITY_TRIGGER_TAMPERED',
  'AUDIT_SHADOW_BACKFILLED',
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
/** 위반 증거 확인 — 감사 행에 걸린 미확인 증거 전부를 사유와 함께 기록(ADMIN, 원복 없음). 응답은 갱신된 체인 상태 */
export async function acknowledgeViolation(auditId: number, reason: string): Promise<{ data: AuditVerifyResult; message: string | null }> {
  const res = await api.post<ApiEnvelope<AuditVerifyResult>>(`/api/audit-logs/violations/${auditId}/ack`, { reason })
  return { data: res.data.data, message: res.data.message }
}

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
