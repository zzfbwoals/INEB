import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import AppLayout from '@/components/layout/AppLayout'
import { listNotices, type NoticeListParams, type NoticeScope, type NoticeSummary } from '@/api/notices'
import type { PageResponse } from '@/api/keys'
import { fmtDate } from '@/lib/format'
import { subscribeUiEvents } from '@/lib/events'
import { useAutoPageSize } from '@/lib/usePageSize'
import { useColumnResize } from '@/lib/useColumnResize'
import { SortMark, sortClass } from '@/components/ui/sort-mark'
import { Pager } from '@/components/ui/pager'
import { Button } from '@/components/ui/button'
import { errorMessage, useToast } from '@/components/ui/toast'
import { NoticeFormDialog } from '@/components/notices/NoticeFormDialog'

/* 목업 notices.html — 공지사항 목록. 상단 고정(pinned) 공지는 서버 정렬로 항상 상단, 검색은 범위(제목+내용/제목/작성자) 즉시검색.
   페이지 크기는 화면 높이에 맞춰 자동 계산(스크롤 없이 한 화면) */
/* 열 기본 폭(%) — 번호·작성자·제목·조회수·등록일 */
const COLS = [8, 12, 55, 10, 15]

export default function NoticeListPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [keyword, setKeyword] = useState('')
  const [scope, setScope] = useState<NoticeScope>('TITLE_CONTENT')
  const [pinned, setPinned] = useState<'' | 'true' | 'false'>('')
  const [sort, setSort] = useState<{ field: string; dir: 'asc' | 'desc' } | null>(null)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<NoticeSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [reloadTick, setReloadTick] = useState(0)
  const [formOpen, setFormOpen] = useState(false)
  const tblRef = useRef<HTMLDivElement>(null)
  const pageSize = useAutoPageSize(tblRef, 50)
  const { tableRef, widths, resizer } = useColumnResize('notices', COLS)

  useEffect(() => {
    if (!pageSize) return
    let cancelled = false
    setLoading(true)
    const params: NoticeListParams = { keyword, scope, pinned, page, size: pageSize, sort: sort?.field, direction: sort?.dir }
    listNotices(params)
      .then((res) => { if (!cancelled) setData(res) })
      .catch((err) => { if (!cancelled) toast(errorMessage(err), 'error') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [keyword, scope, pinned, page, pageSize, sort, reloadTick, toast])

  // 페이지 크기 변동(창 크기 변경)으로 현재 페이지가 범위를 벗어나면 마지막 페이지로 보정
  useEffect(() => {
    if (data && data.totalPages > 0 && page >= data.totalPages) setPage(data.totalPages - 1)
  }, [data, page])

  // 실시간 갱신 — 등록·수정·삭제·첨부 삭제가 커밋되면 목록 refetch (다운로드는 목록에 영향 없음)
  useEffect(() => {
    return subscribeUiEvents((e) => {
      if (e.action.startsWith('NOTICE') && e.action !== 'NOTICE_FILE_DOWNLOADED') setReloadTick((t) => t + 1)
    })
  }, [])

  function toggleSort(field: string) {
    setPage(0)
    setSort((prev) => (prev?.field === field ? { field, dir: prev.dir === 'asc' ? 'desc' : 'asc' } : { field, dir: 'desc' }))
  }

  const rows = data?.content ?? []

  return (
    <AppLayout>
      <div className="page-h">
        <div><h2>공지사항</h2></div>
        <div className="acts">
          <Button className="px-[11px]" data-tip="등록" aria-label="등록" onClick={() => setFormOpen(true)}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2"><path d="M12 5v14M5 12h14" /></svg>
          </Button>
        </div>
      </div>

      <div className="filters">
        <select className="input" style={{ width: 110 }} value={pinned} onChange={(e) => { setPinned(e.target.value as '' | 'true' | 'false'); setPage(0) }}>
          <option value="">전체</option>
          <option value="true">고정</option>
          <option value="false">일반</option>
        </select>
        <select className="input" style={{ width: 120 }} value={scope} onChange={(e) => { setScope(e.target.value as NoticeScope); setPage(0) }}>
          <option value="TITLE_CONTENT">제목+내용</option>
          <option value="TITLE">제목</option>
          <option value="AUTHOR">작성자</option>
        </select>
        <div className="search">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="7" /><path d="m16.5 16.5 4.5 4.5" /></svg>
          <input className="input" placeholder="검색어 입력" value={keyword} onChange={(e) => { setKeyword(e.target.value); setPage(0) }} />
        </div>
      </div>

      <div className="card">
        <div className="tbl-wrap" ref={tblRef}>
          <table className="tbl-fixed" ref={tableRef}>
            <thead>
              <tr>
                <th style={{ width: `${widths[0]}%` }}>번호{resizer(0)}</th>
                <th style={{ width: `${widths[1]}%` }}>작성자{resizer(1)}</th>
                <th style={{ width: `${widths[2]}%` }}>제목{resizer(2)}</th>
                <th className={sortClass(sort, 'viewCount')} style={{ width: `${widths[3]}%` }} onClick={() => toggleSort('viewCount')}>조회수<SortMark sort={sort} field="viewCount" />{resizer(3)}</th>
                <th className={sortClass(sort, 'createdAt')} style={{ width: `${widths[4]}%` }} onClick={() => toggleSort('createdAt')}>등록일<SortMark sort={sort} field="createdAt" /></th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr><td colSpan={5} className="tbl-empty">{loading ? '불러오는 중…' : '검색 결과가 없습니다'}</td></tr>
              )}
              {rows.map((n) => (
                <tr key={n.id} className="rowlink" onClick={() => navigate(`/notices/${n.id}`)}>
                  <td>{n.pinned ? <span className="tag-imp">고정</span> : <span className="mono" style={{ color: 'var(--text-3)' }}>{n.id}</span>}</td>
                  <td>{n.authorName}</td>
                  <td>
                    <b>{n.title}</b>
                    {n.fileCount > 0 && <span className="att">📎 {n.fileCount}</span>}
                  </td>
                  <td className="mono" style={{ color: 'var(--text-2)' }}>{n.viewCount.toLocaleString()}</td>
                  <td className="mono" style={{ color: 'var(--text-2)' }}>{fmtDate(n.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager page={page} data={data} unit="건" onPage={setPage} />
      </div>

      <NoticeFormDialog edit={null} open={formOpen} onClose={() => setFormOpen(false)} onDone={() => setReloadTick((t) => t + 1)} />
    </AppLayout>
  )
}
