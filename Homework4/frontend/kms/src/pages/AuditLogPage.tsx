import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import AppLayout from '@/components/layout/AppLayout'
import { AUDIT_ACTIONS, downloadAuditCsv, fetchChainStatus, listAuditLogs, verifyAuditChain, type AuditLogItem, type AuditVerifyResult } from '@/api/audit'
import type { PageResponse } from '@/api/keys'
import { Button } from '@/components/ui/button'
import { errorMessage, useToast } from '@/components/ui/toast'
import { subscribeUiEvents } from '@/lib/events'
import { useAutoPageSize } from '@/lib/usePageSize'
import { Pager } from '@/components/ui/pager'

/* 목업 audit.html — 감사 로그. append-only 해시 체인 + 재검증 + CSV 내려받기.
   페이지 크기는 화면 높이에 맞춰 자동 계산(스크롤 없이 한 화면) */
export default function AuditLogPage() {
  const toast = useToast()
  const [searchParams] = useSearchParams()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [actor, setActor] = useState('')
  const [action, setAction] = useState('')
  const [target, setTarget] = useState(() => searchParams.get('target') ?? '')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<AuditLogItem> | null>(null)
  const [loading, setLoading] = useState(true)
  const [verifying, setVerifying] = useState(false)
  const [chain, setChain] = useState<AuditVerifyResult | null | 'unavailable'>(null)
  const [reloadTick, setReloadTick] = useState(0)
  const tblRef = useRef<HTMLDivElement>(null)
  const pageSize = useAutoPageSize(tblRef, 46)

  // 화면 진입 시 체인 상태 자동 검증 (읽기 전용 — 감사 기록 없음)
  useEffect(() => {
    fetchChainStatus().then(setChain).catch(() => setChain('unavailable'))
  }, [])

  // 실시간 갱신 — 모든 행위는 감사 기록되므로 이벤트가 오면 목록·체인 상태를 refetch
  useEffect(() => {
    return subscribeUiEvents(() => {
      setReloadTick((t) => t + 1)
      fetchChainStatus().then(setChain).catch(() => {})
    })
  }, [])

  useEffect(() => {
    if (!pageSize) return
    let cancelled = false
    setLoading(true)
    listAuditLogs({ actor, action, target, from, to, page, size: pageSize })
      .then((res) => { if (!cancelled) setData(res) })
      .catch((err) => { if (!cancelled) toast(errorMessage(err), 'error') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [actor, action, target, from, to, page, pageSize, reloadTick, toast])

  // 페이지 크기 변동(창 크기 변경)으로 현재 페이지가 범위를 벗어나면 마지막 페이지로 보정
  useEffect(() => {
    if (data && data.totalPages > 0 && page >= data.totalPages) setPage(data.totalPages - 1)
  }, [data, page])

  async function runVerify() {
    setVerifying(true)
    try {
      const { data: result, message } = await verifyAuditChain()
      setChain(result)
      toast(message ?? (result.valid ? '해시 체인 검증을 통과했습니다.' : '해시 체인 위반이 감지되었습니다.'),
        result.valid ? 'ok' : 'error')
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setVerifying(false)
    }
  }

  const rows = data?.content ?? []

  return (
    <AppLayout>
      <div className="page-h">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <h2>감사 로그</h2>
          {chain === 'unavailable' && <span className="badge b-deact">체인 확인 불가</span>}
          {chain && chain !== 'unavailable' && (chain.valid
            ? <span className="badge b-active">체인 정상</span>
            : <span className="badge b-bad">체인 위반 {chain.violations.length}건</span>)}
        </div>
        <div className="acts">
          <Button variant="ghost" disabled={verifying} onClick={runVerify}>체인 재검증</Button>
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
          <table className="tbl-fixed">
            <thead>
              <tr>
                <th style={{ width: '7%' }}>ID</th>
                <th style={{ width: '15%' }}>일시 (KST)</th>
                <th style={{ width: '10%' }}>행위자</th>
                <th style={{ width: '17%' }}>행위</th>
                <th style={{ width: '26%' }}>대상</th>
                <th style={{ width: '25%' }}>상세</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr><td colSpan={6} className="tbl-empty">{loading ? '불러오는 중…' : '조건에 맞는 기록이 없습니다'}</td></tr>
              )}
              {rows.map((a) => (
                <tr key={a.id}>
                  <td className="mono" style={{ color: 'var(--text-3)' }}>#{a.id}</td>
                  <td className="mono">{a.createdAt}</td>
                  <td><b>{a.actor}</b></td>
                  <td><span className="actchip">{a.action}</span></td>
                  <td className="mono" style={{ color: 'var(--text-2)' }}>{a.target}</td>
                  <td className="mono" style={{ color: 'var(--text-3)' }} title={a.detail}>{a.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager page={page} data={data} onPage={setPage} />
      </div>
    </AppLayout>
  )
}
