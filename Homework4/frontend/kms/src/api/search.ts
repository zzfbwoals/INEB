import api from './axios'
import type { ApiEnvelope } from './auth'
import type { KeySummary } from './keys'
import type { UserSummary } from './users'
import type { NoticeSummary } from './notices'
import type { AuditLogItem } from './audit'

/* 통합 검색 — GET /api/search?q=&type=. ALL 은 항목별 5건 + 전체 일치 수, 단일 유형은 최대 100건.
   사용자는 서버가 복호화해 이름·연락처·이메일을 부분검색하므로 반드시 서버 API 를 쓴다. */

export type SearchType = 'ALL' | 'KEY' | 'USER' | 'NOTICE' | 'AUDIT'

export interface SearchResponse {
  keys: KeySummary[]
  users: UserSummary[]
  notices: NoticeSummary[]
  audits: AuditLogItem[]
  counts: { keys: number; users: number; notices: number; audits: number }
}

export async function search(q: string, type: SearchType = 'ALL'): Promise<SearchResponse> {
  const res = await api.get<ApiEnvelope<SearchResponse>>('/api/search', { params: { q, type } })
  return res.data.data
}
