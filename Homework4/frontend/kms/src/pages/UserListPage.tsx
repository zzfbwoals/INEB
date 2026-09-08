import { useEffect, useRef, useState } from 'react'
import AppLayout from '@/components/layout/AppLayout'
import { fetchMe } from '@/api/auth'
import { listUsers, type UserListParams, type UserStatus, type UserSummary } from '@/api/users'
import type { PageResponse } from '@/api/keys'
import { fmtDate } from '@/lib/format'
import { subscribeUiEvents } from '@/lib/events'
import { useAutoPageSize } from '@/lib/usePageSize'
import { Pager } from '@/components/ui/pager'
import { Button } from '@/components/ui/button'
import { errorMessage, useToast } from '@/components/ui/toast'
import { IntegrityBadge } from '@/components/keys/StateBadge'
import { UserFormDialog } from '@/components/users/UserFormDialog'
import { UserPlainDialog } from '@/components/users/UserPlainDialog'

/* 목업 users.html — 사용자 관리. 연락처·이메일은 마스킹 표시, 정확검색은 HMAC 해시(전체 값 입력).
   페이지 크기는 화면 높이에 맞춰 자동 계산(스크롤 없이 한 화면) */
export default function UserListPage() {
  const toast = useToast()
  const [keyword, setKeyword] = useState('')
  const [exactInput, setExactInput] = useState('')
  // 정확검색 — 한 필드로 연락처·이메일을 받아 '@' 포함 여부로 판별해 phone/email 파라미터 중 하나로 보낸다
  const [exact, setExact] = useState<{ phone?: string; email?: string }>({})
  const [status, setStatus] = useState<UserStatus | ''>('')
  const [sort, setSort] = useState<{ field: string; dir: 'asc' | 'desc' } | null>(null)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<UserSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [reloadTick, setReloadTick] = useState(0)
  const [isAdmin, setIsAdmin] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<UserSummary | null>(null)
  const [plainTarget, setPlainTarget] = useState<UserSummary | null>(null)
  const tblRef = useRef<HTMLDivElement>(null)
  const pageSize = useAutoPageSize(tblRef, 58)

  useEffect(() => {
    fetchMe().then((me) => setIsAdmin(me.role === 'ADMIN')).catch(() => {})
  }, [])

  useEffect(() => {
    if (!pageSize) return
    let cancelled = false
    setLoading(true)
    const params: UserListParams = { keyword, ...exact, status, page, size: pageSize, sort: sort?.field, direction: sort?.dir }
    listUsers(params)
      .then((res) => { if (!cancelled) setData(res) })
      .catch((err) => { if (!cancelled) toast(errorMessage(err), 'error') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [keyword, exact, status, page, pageSize, sort, reloadTick, toast])

  // 페이지 크기 변동(창 크기 변경)으로 현재 페이지가 범위를 벗어나면 마지막 페이지로 보정
  useEffect(() => {
    if (data && data.totalPages > 0 && page >= data.totalPages) setPage(data.totalPages - 1)
  }, [data, page])

  // 실시간 갱신 — 사용자 관련 행위가 커밋되면 목록 refetch
  useEffect(() => {
    return subscribeUiEvents((e) => {
      if (e.action.startsWith('USER')) setReloadTick((t) => t + 1)
    })
  }, [])

  function toggleSort(field: string) {
    setPage(0)
    setSort((prev) => (prev?.field === field ? { field, dir: prev.dir === 'asc' ? 'desc' : 'asc' } : { field, dir: 'asc' }))
  }

  function exactSearch() {
    const v = exactInput.trim()
    if (!v) {
      setExact({})
      toast('연락처 또는 이메일 전체를 입력해주세요')
      return
    }
    setPage(0)
    setExact(v.includes('@') ? { email: v } : { phone: v })
  }

  const rows = data?.content ?? []

  return (
    <AppLayout>
      <div className="page-h">
        <div><h2>사용자 관리</h2></div>
        <div className="acts">
          <Button className="px-[11px]" data-tip="등록" aria-label="등록" onClick={() => { setEditTarget(null); setFormOpen(true) }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2"><path d="M12 5v14M5 12h14" /></svg>
          </Button>
        </div>
      </div>

      <div className="filters">
        <div className="search">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="7" /><path d="m16.5 16.5 4.5 4.5" /></svg>
          <input className="input" placeholder="이름 검색" value={keyword} onChange={(e) => { setKeyword(e.target.value); setPage(0) }} />
        </div>
        <input className="input mono" style={{ width: 250 }} placeholder="연락처·이메일 정확검색"
          value={exactInput} onChange={(e) => { setExactInput(e.target.value); if (!e.target.value.trim()) setExact({}) }}
          onKeyDown={(e) => { if (e.key === 'Enter') exactSearch() }} />
        <Button variant="ghost" onClick={exactSearch}>검색</Button>
        <select className="input" value={status} onChange={(e) => { setStatus(e.target.value as UserStatus | ''); setPage(0) }}>
          <option value="">상태 전체</option>
          <option value="ACTIVE">활성</option>
          <option value="SUSPENDED">정지</option>
        </select>
      </div>

      <div className="card">
        <div className="tbl-wrap" ref={tblRef}>
          <table className="tbl-fixed">
            <thead>
              <tr>
                <th className="sortable" style={{ width: '16%' }} onClick={() => toggleSort('name')}>사용자 ↕</th>
                <th style={{ width: '15%' }}>연락처</th>
                <th style={{ width: '20%' }}>이메일</th>
                <th style={{ width: '9%' }}>상태</th>
                <th style={{ width: '8%' }}>무결성</th>
                <th className="sortable" style={{ width: '11%' }} onClick={() => toggleSort('createdAt')}>가입일 ↕</th>
                <th style={{ width: '21%' }}></th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr><td colSpan={7} className="tbl-empty">{loading ? '불러오는 중…' : '검색 결과가 없습니다'}</td></tr>
              )}
              {rows.map((u) => (
                <tr key={u.id}>
                  <td>
                    <span className="uavatar" style={{ background: 'var(--blue-bg)', color: 'var(--blue)' }}>{u.name.charAt(0)}</span>
                    <b>{u.name}</b>
                  </td>
                  <td className="mask">{u.phoneMasked}</td>
                  <td className="mask">{u.emailMasked}</td>
                  <td>{u.status === 'ACTIVE'
                    ? <span className="badge b-active">활성</span>
                    : <span className="badge b-deact">정지</span>}</td>
                  <td><IntegrityBadge valid={u.integrityValid} /></td>
                  <td className="mono" style={{ color: 'var(--text-2)' }}>{fmtDate(u.createdAt)}</td>
                  <td>
                    <div style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                      {isAdmin && (
                        <button type="button" className="icon-btn" data-tip="원문 보기" aria-label="원문 보기" onClick={() => setPlainTarget(u)}>
                          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12z" /><circle cx="12" cy="12" r="3" /></svg>
                        </button>
                      )}
                      <button type="button" className="icon-btn" data-tip="수정" aria-label="수정" onClick={() => { setEditTarget(u); setFormOpen(true) }}>
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M4 20h4l10.5-10.5a2.1 2.1 0 0 0-3-3L5 17v3z" /><path d="m13.5 6.5 3 3" /></svg>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager page={page} data={data} unit="명" onPage={setPage} />
      </div>

      <UserFormDialog edit={editTarget} open={formOpen} onClose={() => setFormOpen(false)} onDone={() => setReloadTick((t) => t + 1)} />
      {plainTarget && <UserPlainDialog user={plainTarget} onClose={() => setPlainTarget(null)} />}
    </AppLayout>
  )
}
