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
  const [phoneInput, setPhoneInput] = useState('')
  const [phone, setPhone] = useState('')
  const [status, setStatus] = useState<UserStatus | ''>('')
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
    const params: UserListParams = { keyword, phone, status, page, size: pageSize }
    listUsers(params)
      .then((res) => { if (!cancelled) setData(res) })
      .catch((err) => { if (!cancelled) toast(errorMessage(err), 'error') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [keyword, phone, status, page, pageSize, reloadTick, toast])

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

  function phoneExact() {
    if (!phoneInput.trim()) {
      setPhone('')
      toast('연락처 전체를 입력해주세요')
      return
    }
    setPage(0)
    setPhone(phoneInput.trim())
  }

  const rows = data?.content ?? []

  return (
    <AppLayout crumb="운영 관리 / 사용자 관리">
      <div className="page-h">
        <div><h2>사용자 관리</h2></div>
        <div className="acts">
          <Button onClick={() => { setEditTarget(null); setFormOpen(true) }}>등록</Button>
        </div>
      </div>

      <div className="filters">
        <div className="search">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="7" /><path d="m16.5 16.5 4.5 4.5" /></svg>
          <input className="input" placeholder="이름 검색" value={keyword} onChange={(e) => { setKeyword(e.target.value); setPage(0) }} />
        </div>
        <input className="input mono" style={{ width: 250 }} placeholder="연락처 정확검색 (010-1234-5678)"
          value={phoneInput} onChange={(e) => { setPhoneInput(e.target.value); if (!e.target.value.trim()) setPhone('') }}
          onKeyDown={(e) => { if (e.key === 'Enter') phoneExact() }} />
        <Button variant="ghost" onClick={phoneExact}>검색</Button>
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
                <th style={{ width: '16%' }}>사용자</th>
                <th style={{ width: '15%' }}>연락처</th>
                <th style={{ width: '20%' }}>이메일</th>
                <th style={{ width: '9%' }}>상태</th>
                <th style={{ width: '8%' }}>무결성</th>
                <th style={{ width: '11%' }}>가입일</th>
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
                    <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                      {isAdmin && <Button variant="ghost" size="sm" onClick={() => setPlainTarget(u)}>원문 보기</Button>}
                      <Button variant="ghost" size="sm" onClick={() => { setEditTarget(u); setFormOpen(true) }}>수정</Button>
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
