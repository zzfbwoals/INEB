import { Plus, Search } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import AppLayout from '@/components/layout/AppLayout'
import { listKeys, type KeyAlgorithm, type KeyListParams, type KeyPurpose, type KeySummary, type PageResponse } from '@/api/keys'
import { PURPOSE_KO, algoLabel } from '@/lib/keyRules'
import { dday, fmtDate } from '@/lib/format'
import { subscribeUiEvents } from '@/lib/events'
import { useAutoPageSize } from '@/lib/usePageSize'
import { useColumnResize } from '@/lib/useColumnResize'
import { SortMark, sortClass } from '@/components/ui/sort-mark'
import { Pager } from '@/components/ui/pager'
import { Button } from '@/components/ui/button'
import { errorMessage, useToast } from '@/components/ui/toast'
import { IntegrityBadge, StateBadge } from '@/components/keys/StateBadge'
import { KeyCreateDialog } from '@/components/keys/KeyCreateDialog'

/* 열 기본 폭(%) — 키명·알고리즘·모드·용도·상태·버전·갱신 주기·다음 갱신·무결성 */
const COLS = [17, 12, 6, 13, 14, 11, 8, 12, 8]

/* 목업 keys.html — 키 목록. 페이지 크기는 화면 높이에 맞춰 자동 계산(스크롤 없이 한 화면) */
export default function KeyListPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [keyword, setKeyword] = useState('')
  const [algorithm, setAlgorithm] = useState<KeyAlgorithm | ''>('')
  const [status, setStatus] = useState<KeyListParams['status']>('LIVE')
  const [purpose, setPurpose] = useState<KeyPurpose | ''>('')
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState<{ field: string; dir: 'asc' | 'desc' } | null>(null)
  const [data, setData] = useState<PageResponse<KeySummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [reloadTick, setReloadTick] = useState(0)
  const tblRef = useRef<HTMLDivElement>(null)
  const pageSize = useAutoPageSize(tblRef, 50)
  const { tableRef, widths, resizer } = useColumnResize('keys', COLS)

  useEffect(() => {
    if (!pageSize) return
    let cancelled = false
    setLoading(true)
    listKeys({ keyword, algorithm, status, purpose, page, size: pageSize, sort: sort?.field, direction: sort?.dir })
      .then((res) => { if (!cancelled) setData(res) })
      .catch((err) => { if (!cancelled) toast(errorMessage(err), 'error') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [keyword, algorithm, status, purpose, page, pageSize, sort, reloadTick, toast])

  // 페이지 크기 변동(창 크기 변경)으로 현재 페이지가 범위를 벗어나면 마지막 페이지로 보정
  useEffect(() => {
    if (data && data.totalPages > 0 && page >= data.totalPages) setPage(data.totalPages - 1)
  }, [data, page])

  // 실시간 갱신 — 키 관련 행위(생성·상태 변경·테스트·스케줄러)가 커밋되면 목록 refetch
  useEffect(() => {
    return subscribeUiEvents((e) => {
      if (e.action.startsWith('KEY')) setReloadTick((t) => t + 1)
    })
  }, [])

  function toggleSort(field: string) {
    setPage(0)
    setSort((prev) => (prev?.field === field ? { field, dir: prev.dir === 'asc' ? 'desc' : 'asc' } : { field, dir: 'asc' }))
  }

  const rows = data?.content ?? []

  return (
    <AppLayout>
      <div className="page-h">
        <div><h2>키 목록</h2></div>
      </div>

      <div className="filters">
        <div className="search">
          <Search size={14} />
          <input className="input" placeholder="키명 검색" value={keyword} onChange={(e) => { setKeyword(e.target.value); setPage(0) }} />
        </div>
        <select className="input" value={algorithm} onChange={(e) => { setAlgorithm(e.target.value as KeyAlgorithm | ''); setPage(0) }}>
          <option value="">알고리즘 전체</option>
          {(['AES', 'ARIA', 'LEA', 'SEED', 'RSA', 'ECDSA', 'SHA256', 'SHA512'] as KeyAlgorithm[]).map((a) => <option key={a} value={a}>{a}</option>)}
        </select>
        <select className="input" value={status} onChange={(e) => { setStatus(e.target.value as KeyListParams['status']); setPage(0) }}>
          <option value="LIVE">상태 전체 (폐기 제외)</option>
          <option value="PRE_ACTIVE">PRE_ACTIVE · 준비</option>
          <option value="ACTIVE">ACTIVE · 운영</option>
          <option value="DEACTIVATED">DEACTIVATED · 정지</option>
          <option value="DESTROYED">DESTROYED · 폐기</option>
          <option value="ALL">모두 표시</option>
        </select>
        <select className="input" value={purpose} onChange={(e) => { setPurpose(e.target.value as KeyPurpose | ''); setPage(0) }}>
          <option value="">용도 전체</option>
          <option value="ENC_DEC">암/복호화</option>
          <option value="ENC_DEC_SIGN_VERIFY">암/복호화 및 서명/검증</option>
          <option value="SIGN_VERIFY">서명/검증</option>
        </select>
        <Button className="px-[11px] ml-auto" data-tip="등록" aria-label="등록" onClick={() => setCreateOpen(true)}>
          <Plus size={16} strokeWidth={2.2} />
        </Button>
      </div>

      <div className="card">
        <div className="tbl-wrap" ref={tblRef}>
          <table className="tbl-fixed" ref={tableRef}>
            <thead>
              <tr>
                <th className={sortClass(sort, 'keyName')} style={{ width: `${widths[0]}%` }} onClick={() => toggleSort('keyName')}>키명<SortMark sort={sort} field="keyName" />{resizer(0)}</th>
                <th className={sortClass(sort, 'algorithm')} style={{ width: `${widths[1]}%` }} onClick={() => toggleSort('algorithm')}>알고리즘<SortMark sort={sort} field="algorithm" />{resizer(1)}</th>
                <th className={sortClass(sort, 'mode')} style={{ width: `${widths[2]}%` }} onClick={() => toggleSort('mode')}>모드<SortMark sort={sort} field="mode" />{resizer(2)}</th>
                <th className={sortClass(sort, 'purpose')} style={{ width: `${widths[3]}%` }} onClick={() => toggleSort('purpose')}>용도<SortMark sort={sort} field="purpose" />{resizer(3)}</th>
                <th className={sortClass(sort, 'status')} style={{ width: `${widths[4]}%` }} onClick={() => toggleSort('status')}>상태<SortMark sort={sort} field="status" />{resizer(4)}</th>
                <th style={{ width: `${widths[5]}%` }}>버전{resizer(5)}</th>
                <th style={{ width: `${widths[6]}%` }}>갱신 주기{resizer(6)}</th>
                <th className={sortClass(sort, 'nextRotationAt')} style={{ width: `${widths[7]}%` }} onClick={() => toggleSort('nextRotationAt')}>다음 갱신<SortMark sort={sort} field="nextRotationAt" />{resizer(7)}</th>
                <th style={{ width: `${widths[8]}%` }}>무결성</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr><td colSpan={9} className="tbl-empty">{loading ? '불러오는 중…' : '조건에 맞는 키가 없습니다 — 필터를 조정해 보세요'}</td></tr>
              )}
              {rows.map((k) => <KeyRow key={k.keyUid} k={k} onClick={() => navigate(`/keys/${k.keyUid}`)} />)}
            </tbody>
          </table>
        </div>
        <Pager page={page} data={data} onPage={setPage} />
      </div>

      <KeyCreateDialog open={createOpen} onOpenChange={setCreateOpen} onCreated={() => setReloadTick((t) => t + 1)} />
    </AppLayout>
  )
}

function KeyRow({ k, onClick }: { k: KeySummary; onClick: () => void }) {
  const d = dday(k.nextRotationAt)
  let rotCell: React.ReactNode
  if (k.status === 'DESTROYED' || k.status === 'DEACTIVATED') rotCell = <span style={{ color: 'var(--text-3)' }}>—</span>
  else if (k.status === 'PRE_ACTIVE') rotCell = <span style={{ color: 'var(--blue)' }}>활성 예정 {fmtDate(k.activationDate)}</span>
  else if (!k.autoRotate) rotCell = <span style={{ color: 'var(--text-3)' }}>수동</span>
  else rotCell = (
    <span style={{ color: d !== null && d <= 30 ? 'var(--red)' : 'var(--text-2)' }}>
      {fmtDate(k.nextRotationAt)}
      {d !== null && d > 0 && d <= 30 && <b> (D-{d})</b>}
      {d !== null && d <= 0 && <b> (지연)</b>}
    </span>
  )
  return (
    <tr className="rowlink" onClick={onClick}>
      <td><b>{k.keyName}</b></td>
      <td className="mono">{algoLabel(k.algorithm, k.keySize)}</td>
      <td className="mono" style={{ color: 'var(--text-2)' }}>{k.mode ?? '—'}</td>
      <td>{PURPOSE_KO[k.purpose]}</td>
      <td><StateBadge state={k.status} /></td>
      <td>
        <span className="vtag">v{k.currentVersion}</span>{' '}
        <span style={{ fontSize: 11.5, color: 'var(--text-3)' }}>/ {k.versionCount}</span>
        {k.scheduledVersion && <> <span className="vtag sched" title={`활성일 ${k.scheduledAt}`}>↻ v{k.scheduledVersion} 예약</span></>}
      </td>
      <td className="mono" style={{ color: 'var(--text-2)' }}>{k.autoRotate ? `${k.rotationPeriodDays}일` : <span style={{ color: 'var(--text-3)' }}>—</span>}</td>
      <td className="mono">{rotCell}</td>
      <td><IntegrityBadge valid={k.integrityValid} /></td>
    </tr>
  )
}
