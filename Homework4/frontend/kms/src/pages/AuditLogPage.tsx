import { Download, List, RotateCw } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useLocation, useSearchParams } from 'react-router'
import AppLayout from '@/components/layout/AppLayout'
import {
  AUDIT_ACTIONS, downloadAuditCsv, fetchAuditActors, fetchChainStatus, fetchForensics, listAuditLogs, shadowHasIssue, verifyAuditChain,
  type AuditForensics, type AuditLogItem, type AuditModifiedItem, type AuditVerifyResult,
} from '@/api/audit'
import type { PageResponse } from '@/api/keys'
import { errorMessage, useToast } from '@/components/ui/toast'
import { subscribeUiEvents } from '@/lib/events'
import { useAutoPageSize } from '@/lib/usePageSize'
import { useColumnResize } from '@/lib/useColumnResize'
import { SortMark, sortClass } from '@/components/ui/sort-mark'
import { Pager } from '@/components/ui/pager'
import { AuditForensicsDialog, FIELD_KO, type ForensicsView } from '@/components/audit/AuditForensicsDialog'
import { AuditLogDetailDialog } from '@/components/audit/AuditLogDetailDialog'

/* 목업 audit.html — 감사 로그. append-only 해시 체인 + 섀도(복사본) 비교 + CSV 내려받기.
   목록은 원본 테이블 기준으로 보여주고, 섀도 비교 결과(수정·삽입)를 행에 표시하며 지워진 행은 유령 행으로 끼워 넣는다.
   페이지 크기는 화면 높이에 맞춰 자동 계산(스크롤 없이 한 화면) */
/* 열 기본 폭(%) — ID·일시·행위자·행위·대상·상세 */
const COLS = [6, 14, 9, 15, 22, 34]
/** 페이지당 인라인 유령 행 상한 — 초과분은 한 줄로 접는다 (페이지 크기는 건드리지 않는다) */
const GHOST_MAX = 3

type Row = { kind: 'row'; item: AuditLogItem } | { kind: 'ghost'; item: AuditLogItem } | { kind: 'more'; count: number }

export default function AuditLogPage() {
  const toast = useToast()
  const [searchParams] = useSearchParams()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [actor, setActor] = useState('')
  const [action, setAction] = useState('')
  const [target, setTarget] = useState(() => searchParams.get('target') ?? '')
  // 표시 범위 — 전체 / 정상(위반 행 제외) / 위반(서버 목록 대신 섀도 비교 forensics 의 수정·삽입·삭제 행만, 필터·정렬·페이징은 클라이언트)
  const [view, setView] = useState<'all' | 'ok' | 'bad'>('all')
  const onlyBad = view === 'bad'
  // 행위자 콤보박스 — 기록에 존재하는 행위자 목록(실시간 이벤트마다 갱신)
  const [actors, setActors] = useState<string[]>([])
  const [sort, setSort] = useState<{ field: string; dir: 'asc' | 'desc' } | null>(null)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<AuditLogItem> | null>(null)
  const [loading, setLoading] = useState(true)
  const [verifying, setVerifying] = useState(false)
  const [chain, setChain] = useState<AuditVerifyResult | null | 'unavailable'>(null)
  const [forensics, setForensics] = useState<AuditForensics | null>(null)
  const [forensicsOpen, setForensicsOpen] = useState<ForensicsView | null>(null)
  const location = useLocation()
  // 통합 검색·대시보드 최근 활동에서 진입 — state.detail 이 있으면 그 행의 상세 모달을 바로 연다
  const [detailItem, setDetailItem] = useState<AuditLogItem | null>(() => (location.state as { detail?: AuditLogItem } | null)?.detail ?? null)
  const [reloadTick, setReloadTick] = useState(0)
  const tblRef = useRef<HTMLDivElement>(null)
  const pageSize = useAutoPageSize(tblRef, 46)
  const { tableRef, widths, resizer } = useColumnResize('audit', COLS)

  // 체인 상태(원본 체인·섀도 요약·보호 트리거) — 문제가 있을 때만 상세(forensics)를 추가로 받는다 (비용 절감)
  const refreshChain = useCallback(async () => {
    try {
      const status = await fetchChainStatus()
      setChain(status)
      setForensics(shadowHasIssue(status) || !status.valid ? await fetchForensics() : null)
    } catch {
      setChain('unavailable')
      setForensics(null)
    }
  }, [])

  useEffect(() => { refreshChain() }, [refreshChain])
  useEffect(() => { fetchAuditActors().then(setActors).catch(() => {}) }, [reloadTick])

  // 실시간 갱신 — 모든 행위는 감사 기록되므로 이벤트가 오면 목록·체인 상태를 refetch
  useEffect(() => {
    return subscribeUiEvents(() => {
      setReloadTick((t) => t + 1)
      refreshChain()
    })
  }, [refreshChain])

  useEffect(() => {
    if (!pageSize || onlyBad) return
    let cancelled = false
    setLoading(true)
    listAuditLogs({ actor, action, target, from, to, page, size: pageSize, sort: sort?.field, direction: sort?.dir })
      .then((res) => { if (!cancelled) setData(res) })
      .catch((err) => { if (!cancelled) toast(errorMessage(err), 'error') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [actor, action, target, from, to, page, pageSize, sort, reloadTick, toast, onlyBad])

  // 페이지 크기 변동(창 크기 변경)으로 현재 페이지가 범위를 벗어나면 마지막 페이지로 보정
  useEffect(() => {
    if (onlyBad) return
    if (data && data.totalPages > 0 && page >= data.totalPages) setPage(data.totalPages - 1)
  }, [data, page, onlyBad])

  function toggleSort(field: string) {
    setPage(0)
    setSort((prev) => (prev?.field === field ? { field, dir: prev.dir === 'asc' ? 'desc' : 'asc' } : { field, dir: 'asc' }))
  }

  async function runVerify() {
    setVerifying(true)
    try {
      const { data: result, message } = await verifyAuditChain()
      setChain(result)
      setForensics(shadowHasIssue(result) || !result.valid ? await fetchForensics() : null)
      toast(message ?? (result.healthy ? '해시 체인 검증을 통과했습니다.' : '감사 로그 위반이 감지되었습니다.'),
        result.healthy ? 'ok' : 'error')
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setVerifying(false)
    }
  }

  const rows = data?.content ?? []
  const modifiedById = new Map<number, AuditModifiedItem>(forensics?.modified.map((m) => [m.id, m]) ?? [])
  const insertedIds = new Set(forensics?.inserted.map((r) => r.id) ?? [])
  // 유령 행(지워진 행)은 기본 정렬(id 내림차순)·필터 없음일 때만 끼워 넣는다 — 필터가 있으면 페이지 id 범위가 불연속이라 위치를 정할 수 없다
  const inlineGhosts = !sort && !actor && !action && !target && !from && !to
  // '위반 행만' — forensics 의 수정(현재 값)·삽입·삭제(섀도 값) 행을 합쳐 클라이언트에서 필터·정렬·페이징
  const badAll: Row[] = onlyBad && forensics
    ? [
      ...forensics.modified.map((m): Row => ({ kind: 'row', item: m.current })),
      ...forensics.inserted.map((item): Row => ({ kind: 'row', item })),
      ...forensics.deleted.map((item): Row => ({ kind: 'ghost', item })),
    ].filter((r) => r.kind !== 'more' && matchesFilter(r.item, { actor, action, target, from, to }))
      .sort((a, b) => compareRows(a, b, sort))
    : []
  const badPage = onlyBad && pageSize
    ? { totalElements: badAll.length, totalPages: Math.ceil(badAll.length / pageSize) }
    : null
  const merged = onlyBad
    ? badAll.slice(page * (pageSize || 1), (page + 1) * (pageSize || 1))
    : inlineGhosts && forensics && forensics.deleted.length > 0
      ? mergeGhosts(rows, forensics.deleted, page === 0, !!data && page >= data.totalPages - 1)
      : rows.map((item): Row => ({ kind: 'row', item }))
  const pageInfo = onlyBad ? badPage : data
  // '정상' 은 현재 페이지에서 수정·삽입·삭제 행을 제외 (페이지 총계는 서버 기준이라 위반 행 수만큼 적게 보일 수 있음)
  const shown = view === 'ok'
    ? merged.filter((r) => r.kind === 'row' && !modifiedById.has(r.item.id) && !insertedIds.has(r.item.id))
    : merged
  const summary = chain && chain !== 'unavailable' ? chain.shadow : undefined
  const unhealthy = chain && chain !== 'unavailable' && !chain.healthy

  return (
    <AppLayout>
      <div className="page-h">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          <h2>감사 로그</h2>
          {chain === 'unavailable' && <span className="badge b-deact">체인 확인 불가</span>}
          {chain && chain !== 'unavailable' && (chain.healthy
            ? <span className="badge b-active">체인 정상</span>
            : <span className="badge b-bad">{badgeText(chain)}</span>)}
          {summary && summary.guard !== 'ACTIVE' && <span className="badge b-bad">섀도 보호 {summary.guard === 'DISABLED' ? '해제' : '누락'}</span>}
          {unhealthy && forensics && (
            <button type="button" className="icon-btn" data-tip="위반 상세" aria-label="위반 상세" onClick={() => setForensicsOpen({ mode: 'all', tab: 'deleted' })}>
              <List size={15} />
            </button>
          )}
          <button type="button" className="icon-btn" data-tip="체인 재검증" aria-label="체인 재검증" disabled={verifying} onClick={runVerify}>
            <RotateCw size={15} />
          </button>
          <button type="button" className="icon-btn" data-tip="CSV 내려받기" aria-label="CSV 내려받기" onClick={async () => {
            try {
              await downloadAuditCsv({ actor, action, target, from, to })
              toast('CSV 다운로드를 시작합니다.')
            } catch (err) {
              toast(errorMessage(err), 'error')
            }
          }}>
            <Download size={15} />
          </button>
        </div>
      </div>

      <div className="filters">
        <input className="input" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0) }} />
        <span style={{ color: 'var(--text-3)' }}>→</span>
        <input className="input" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0) }} />
        <select className="input" value={actor} onChange={(e) => { setActor(e.target.value); setPage(0) }}>
          <option value="">행위자 전체</option>
          {actors.map((a) => <option key={a} value={a}>{a}</option>)}
        </select>
        <select className="input" value={action} onChange={(e) => { setAction(e.target.value); setPage(0) }}>
          <option value="">행위유형 전체</option>
          {AUDIT_ACTIONS.map((a) => <option key={a} value={a}>{a}</option>)}
        </select>
        <div className="seg">
          {([['all', '전체'], ['ok', '정상'], ['bad', '위반']] as const).map(([v, l]) => (
            <button key={v} type="button" className={view === v ? 'on' : ''} onClick={() => { setView(v); setPage(0) }}>{l}</button>
          ))}
        </div>
        <input className="input mono" style={{ width: 250 }} placeholder="대상 (KEY#uid / USER#id)" value={target}
          onChange={(e) => { setTarget(e.target.value); setPage(0) }} />
      </div>

      <div className="card">
        <div className="tbl-wrap" ref={tblRef}>
          <table className="tbl-fixed" ref={tableRef}>
            <thead>
              <tr>
                <th className={sortClass(sort, 'id')} style={{ width: `${widths[0]}%` }} onClick={() => toggleSort('id')}>ID<SortMark sort={sort} field="id" />{resizer(0)}</th>
                <th className={sortClass(sort, 'createdAt')} style={{ width: `${widths[1]}%` }} onClick={() => toggleSort('createdAt')}>일시 (KST)<SortMark sort={sort} field="createdAt" />{resizer(1)}</th>
                <th className={sortClass(sort, 'actor')} style={{ width: `${widths[2]}%` }} onClick={() => toggleSort('actor')}>행위자<SortMark sort={sort} field="actor" />{resizer(2)}</th>
                <th className={sortClass(sort, 'action')} style={{ width: `${widths[3]}%` }} onClick={() => toggleSort('action')}>행위<SortMark sort={sort} field="action" />{resizer(3)}</th>
                <th className={sortClass(sort, 'target')} style={{ width: `${widths[4]}%` }} onClick={() => toggleSort('target')}>대상<SortMark sort={sort} field="target" />{resizer(4)}</th>
                <th style={{ width: `${widths[5]}%` }}>상세</th>
              </tr>
            </thead>
            <tbody>
              {shown.length === 0 && (
                <tr><td colSpan={6} className="tbl-empty">{onlyBad ? '위반 행이 없습니다' : loading ? '불러오는 중…' : '조건에 맞는 기록이 없습니다'}</td></tr>
              )}
              {shown.map((r) => {
                if (r.kind === 'more') {
                  return (
                    <tr key="more" className="row-ghost">
                      <td colSpan={6} style={{ textAlign: 'center', textDecoration: 'none' }}>
                        …외 {r.count}건 삭제됨 — <button type="button" className="linkish" onClick={() => setForensicsOpen({ mode: 'all', tab: 'deleted' })}>위반 상세</button>
                      </td>
                    </tr>
                  )
                }
                const a = r.item
                if (r.kind === 'ghost') {
                  return <LogRow key={`g${a.id}`} item={a} className="row-ghost" badge={<span className="row-tag">삭제됨</span>} onClick={() => setForensicsOpen({ mode: 'single', kind: 'deleted', id: a.id })} />
                }
                const modified = modifiedById.get(a.id)
                if (modified) {
                  // 바뀐 컬럼을 태그에 함께 표시 — 증거 기반이라 DB 를 원복한 뒤에도 그대로 남는다
                  return <LogRow key={a.id} item={a} className="row-bad rowlink" badge={<span className="row-tag">수정됨 · {modified.fields.map((f) => FIELD_KO[f] ?? f).join(', ')}</span>} onClick={() => setForensicsOpen({ mode: 'single', kind: 'modified', id: a.id })} />
                }
                if (insertedIds.has(a.id)) {
                  return <LogRow key={a.id} item={a} className="row-bad rowlink" badge={<span className="row-tag">삽입됨</span>} onClick={() => setForensicsOpen({ mode: 'single', kind: 'inserted', id: a.id })} />
                }
                return <LogRow key={a.id} item={a} className="rowlink" onClick={() => setDetailItem(a)} />
              })}
            </tbody>
          </table>
        </div>
        <Pager page={page} data={pageInfo} onPage={setPage} />
      </div>

      {forensicsOpen && forensics && (
        <AuditForensicsDialog data={forensics} view={forensicsOpen} onClose={() => setForensicsOpen(null)} />
      )}
      {detailItem && <AuditLogDetailDialog item={detailItem} onClose={() => setDetailItem(null)} />}
    </AppLayout>
  )
}

function LogRow({ item, className, badge, onClick }: { item: AuditLogItem; className?: string; badge?: ReactNode; onClick?: () => void }) {
  return (
    <tr className={className} onClick={onClick}>
      <td className="mono" style={{ color: 'var(--text-3)' }}>#{item.id}</td>
      <td className="mono">{item.createdAt}</td>
      <td><b>{item.actor}</b></td>
      <td><span className="actchip">{item.action}</span></td>
      <td className="mono" style={{ color: 'var(--text-2)' }}>{item.target}</td>
      <td className="mono" style={{ color: 'var(--text-3)' }} title={item.detail}>{badge}{badge && ' '}{item.detail}</td>
    </tr>
  )
}

/** 헤더 배지 문구 — 체인 위반 건수만. 건수는 남아 있는 변조 증거 행 수와 지금 체인 위반 구간 수 중 큰 값
    (감사 로그 위반은 영구라 DB 를 원복해도 증거 행 수는 줄지 않는다) */
function badgeText(chain: AuditVerifyResult): string {
  return `체인 위반 ${Math.max(chain.flaggedRows, chain.violations.length)}건`
}

/**
 * 지워진 행(섀도 값)을 id 내림차순 페이지에 끼워 넣는다.
 * 페이지 [min,max] 안의 삭제 행은 제 위치에, 1페이지에는 max 보다 큰 id(꼬리 삭제)를 맨 위에, 마지막 페이지에는 min 보다 작은 id 를 맨 아래에.
 * 페이지당 GHOST_MAX 개까지만 인라인, 초과분은 "…외 N건" 한 줄.
 */
/** '위반 행만' 보기의 클라이언트 필터 — 행위자·대상은 부분일치, 행위유형은 일치, 기간은 KST 날짜 문자열 비교 */
function matchesFilter(a: AuditLogItem, f: { actor: string; action: string; target: string; from: string; to: string }): boolean {
  const day = a.createdAt.slice(0, 10)
  return (!f.actor || a.actor.toLowerCase().includes(f.actor.toLowerCase()))
    && (!f.action || a.action === f.action)
    && (!f.target || a.target.toLowerCase().includes(f.target.toLowerCase()))
    && (!f.from || day >= f.from) && (!f.to || day <= f.to)
}

function compareRows(a: Row, b: Row, sort: { field: string; dir: 'asc' | 'desc' } | null): number {
  if (a.kind === 'more' || b.kind === 'more') return 0
  const f = (sort?.field ?? 'id') as keyof AuditLogItem
  const x = a.item[f], y = b.item[f]
  const c = typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y))
  return (sort?.dir ?? 'desc') === 'asc' ? c : -c
}

function mergeGhosts(rows: AuditLogItem[], deleted: AuditLogItem[], firstPage: boolean, lastPage: boolean): Row[] {
  const sortedDeleted = [...deleted].sort((a, b) => b.id - a.id)
  const max = rows.length > 0 ? rows[0].id : Number.NEGATIVE_INFINITY
  const min = rows.length > 0 ? rows[rows.length - 1].id : Number.POSITIVE_INFINITY
  const candidates = sortedDeleted.filter((d) =>
    (d.id < max && d.id > min) || (firstPage && d.id > max) || (lastPage && d.id < min) || rows.length === 0)
  const shown = candidates.slice(0, GHOST_MAX)
  const out: Row[] = []
  let gi = 0
  // 위쪽(꼬리 삭제)부터 — 내림차순이므로 현재 행보다 id 가 큰 유령을 먼저 출력
  for (const item of rows) {
    while (gi < shown.length && shown[gi].id > item.id) out.push({ kind: 'ghost', item: shown[gi++] })
    out.push({ kind: 'row', item })
  }
  while (gi < shown.length) out.push({ kind: 'ghost', item: shown[gi++] })
  if (candidates.length > shown.length) out.push({ kind: 'more', count: candidates.length - shown.length })
  return out
}
