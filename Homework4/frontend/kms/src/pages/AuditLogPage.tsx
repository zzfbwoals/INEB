import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import AppLayout from '@/components/layout/AppLayout'
import {
  AUDIT_ACTIONS, downloadAuditCsv, fetchChainStatus, fetchForensics, listAuditLogs, shadowHasIssue, verifyAuditChain,
  type AuditForensics, type AuditLogItem, type AuditModifiedItem, type AuditVerifyResult,
} from '@/api/audit'
import type { PageResponse } from '@/api/keys'
import { Button } from '@/components/ui/button'
import { errorMessage, useToast } from '@/components/ui/toast'
import { subscribeUiEvents } from '@/lib/events'
import { useAutoPageSize } from '@/lib/usePageSize'
import { useColumnResize } from '@/lib/useColumnResize'
import { SortMark, sortClass } from '@/components/ui/sort-mark'
import { Pager } from '@/components/ui/pager'
import { AuditForensicsDialog, type ForensicsView } from '@/components/audit/AuditForensicsDialog'
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
  const [sort, setSort] = useState<{ field: string; dir: 'asc' | 'desc' } | null>(null)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<AuditLogItem> | null>(null)
  const [loading, setLoading] = useState(true)
  const [verifying, setVerifying] = useState(false)
  const [chain, setChain] = useState<AuditVerifyResult | null | 'unavailable'>(null)
  const [forensics, setForensics] = useState<AuditForensics | null>(null)
  const [forensicsOpen, setForensicsOpen] = useState<ForensicsView | null>(null)
  const [detailItem, setDetailItem] = useState<AuditLogItem | null>(null)
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

  // 실시간 갱신 — 모든 행위는 감사 기록되므로 이벤트가 오면 목록·체인 상태를 refetch
  useEffect(() => {
    return subscribeUiEvents(() => {
      setReloadTick((t) => t + 1)
      refreshChain()
    })
  }, [refreshChain])

  useEffect(() => {
    if (!pageSize) return
    let cancelled = false
    setLoading(true)
    listAuditLogs({ actor, action, target, from, to, page, size: pageSize, sort: sort?.field, direction: sort?.dir })
      .then((res) => { if (!cancelled) setData(res) })
      .catch((err) => { if (!cancelled) toast(errorMessage(err), 'error') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [actor, action, target, from, to, page, pageSize, sort, reloadTick, toast])

  // 페이지 크기 변동(창 크기 변경)으로 현재 페이지가 범위를 벗어나면 마지막 페이지로 보정
  useEffect(() => {
    if (data && data.totalPages > 0 && page >= data.totalPages) setPage(data.totalPages - 1)
  }, [data, page])

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
  const merged = inlineGhosts && forensics && forensics.deleted.length > 0
    ? mergeGhosts(rows, forensics.deleted, page === 0, !!data && page >= data.totalPages - 1)
    : rows.map((item): Row => ({ kind: 'row', item }))
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
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M8 6h13M8 12h13M8 18h13" /><path d="M3 6h.01M3 12h.01M3 18h.01" strokeWidth="3" strokeLinecap="round" /></svg>
            </button>
          )}
          <button type="button" className="icon-btn" data-tip="체인 재검증" aria-label="체인 재검증" disabled={verifying} onClick={runVerify}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M20 12a8 8 0 1 1-2.3-5.7" /><path d="M20 4v5h-5" /></svg>
          </button>
        </div>
        <div className="acts">
          <Button onClick={async () => {
            try {
              await downloadAuditCsv({ actor, action, target, from, to })
              toast('CSV 다운로드를 시작합니다.')
            } catch (err) {
              toast(errorMessage(err), 'error')
            }
          }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 4v11m0 0 4.5-4.5M12 15l-4.5-4.5M4 19h16" /></svg>
            CSV 내려받기
          </Button>
        </div>
      </div>

      <div className="filters">
        <input className="input" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0) }} />
        <span style={{ color: 'var(--text-3)' }}>→</span>
        <input className="input" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0) }} />
        <input className="input" style={{ width: 140 }} placeholder="행위자" value={actor}
          onChange={(e) => { setActor(e.target.value); setPage(0) }} />
        <select className="input" value={action} onChange={(e) => { setAction(e.target.value); setPage(0) }}>
          <option value="">행위유형 전체</option>
          {AUDIT_ACTIONS.map((a) => <option key={a} value={a}>{a}</option>)}
        </select>
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
              {merged.length === 0 && (
                <tr><td colSpan={6} className="tbl-empty">{loading ? '불러오는 중…' : '조건에 맞는 기록이 없습니다'}</td></tr>
              )}
              {merged.map((r) => {
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
                  return <LogRow key={a.id} item={a} className="row-bad rowlink" badge={<span className="row-tag">수정됨</span>} onClick={() => setForensicsOpen({ mode: 'single', kind: 'modified', id: a.id })} />
                }
                if (insertedIds.has(a.id)) {
                  return <LogRow key={a.id} item={a} className="row-bad rowlink" badge={<span className="row-tag">삽입됨</span>} onClick={() => setForensicsOpen({ mode: 'single', kind: 'inserted', id: a.id })} />
                }
                return <LogRow key={a.id} item={a} className="rowlink" onClick={() => setDetailItem(a)} />
              })}
            </tbody>
          </table>
        </div>
        <Pager page={page} data={data} onPage={setPage} />
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

/** 헤더 배지 문구 — 체인 위반 구간 수만 (삭제·삽입·수정 내역은 위반 상세 아이콘으로) */
function badgeText(chain: AuditVerifyResult): string {
  return `체인 위반 ${chain.violations.length}건`
}

/**
 * 지워진 행(섀도 값)을 id 내림차순 페이지에 끼워 넣는다.
 * 페이지 [min,max] 안의 삭제 행은 제 위치에, 1페이지에는 max 보다 큰 id(꼬리 삭제)를 맨 위에, 마지막 페이지에는 min 보다 작은 id 를 맨 아래에.
 * 페이지당 GHOST_MAX 개까지만 인라인, 초과분은 "…외 N건" 한 줄.
 */
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
