import api from './axios'
import type { ApiEnvelope } from './auth'

/* 대시보드 집계 API — 조회 전용(감사 기록 없음). 모든 카드는 SSE 이벤트 수신 시 refetch 한다. */

export interface KeyStats {
  total: number
  byStatus: Record<'PRE_ACTIVE' | 'ACTIVE' | 'DEACTIVATED' | 'DESTROYED', number>
  versions: number
  decryptOnly: number
  scheduled: number
  destroyPending: number
}

export interface UserStats {
  total: number
  active: number
  suspended: number
  joined30d: number
}

export interface NoticeStats {
  total: number
  pinned: number
  thisMonth: number
  files: number
}

export interface IntegrityStats {
  keyMeta: number
  keyVersion: number
  user: number
  auditChain: number
  total: number
  firstKeyUid: string | null
  firstKeyName: string | null
}

export interface Signal {
  key: string
  label: string
  value: number
  sub: string
  level: 'warn' | 'bad' | string
}

export interface Failure {
  keyUid: string
  keyName: string
  version: number
  operation: string
  failReason: string | null
  usedAt: string
}

export interface AlgoCount {
  algorithm: string
  count: number
}

export interface DashboardSummary {
  keys: KeyStats
  users: UserStats
  notices: NoticeStats
  integrity: IntegrityStats
  signals: Signal[]
  failures: Failure[]
  algorithms: AlgoCount[]
}

export type TrendOp = 'ALL' | 'ENC' | 'SIGN'
export type TrendDays = 7 | 30

export interface TrendPoint {
  date: string
  ok: number
  fail: number
}

export interface UsageTrend {
  days: number
  op: TrendOp
  points: TrendPoint[]
}

export interface ExpiringItem {
  keyUid: string
  keyName: string
  algorithm: string
  keySize: number
  version: number
  kind: 'ROTATION' | 'ACTIVATION'
  at: string
  dday: number
}

export async function fetchSummary(): Promise<DashboardSummary> {
  const res = await api.get<ApiEnvelope<DashboardSummary>>('/api/dashboard/summary')
  return res.data.data
}

export async function fetchUsageTrend(days: TrendDays, op: TrendOp): Promise<UsageTrend> {
  const res = await api.get<ApiEnvelope<UsageTrend>>('/api/dashboard/usage-trend', { params: { days, op } })
  return res.data.data
}

export async function fetchExpiring(days = 30): Promise<ExpiringItem[]> {
  const res = await api.get<ApiEnvelope<ExpiringItem[]>>('/api/dashboard/expiring', { params: { days } })
  return res.data.data
}
