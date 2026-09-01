import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import AppLayout from '@/components/layout/AppLayout'
import { AUDIT_ACTIONS, downloadAuditCsv, listAuditLogs, verifyAuditChain, type AuditLogItem } from '@/api/audit'
import type { PageResponse } from '@/api/keys'
import { Button } from '@/components/ui/button'
import { errorMessage, useToast } from '@/components/ui/toast'

const PAGE_SIZE = 20

/* 목업 audit.html — 감사 로그. append-only 해시 체인 + 재검증 + CSV 내려받기 */
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

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    listAuditLogs({ actor, action, target, from, to, page, size: PAGE_SIZE })
      .then((res) => { if (!cancelled) setData(res) })
      .catch((err) => { if (!cancelled) toast(errorMessage(err), 'error') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [actor, action, target, from, to, page, toast])

  async function runVerify() {
    setVerifying(true)
    try {
      const { data: result, message } = await verifyAuditChain()
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
    <AppLayout crumb="운영 관리 / 감사 로그">
      <div className="page-h">
        <div><h2>감사 로그</h2></div>
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
        <div className="tbl-wrap">
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
        <div className="pager">
          <span className="pinfo">총 {data?.totalElements ?? 0}건 · {(data?.page ?? 0) + 1}/{Math.max(data?.totalPages ?? 1, 1)} 페이지</span>
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>‹</button>
          {Array.from({ length: Math.max(data?.totalPages ?? 1, 1) }, (_, i) => (
            <button key={i} type="button" className={i === page ? 'on' : ''} onClick={() => setPage(i)}>{i + 1}</button>
          ))}
          <button type="button" disabled={page >= (data?.totalPages ?? 1) - 1} onClick={() => setPage((p) => p + 1)}>›</button>
        </div>
      </div>
    </AppLayout>
  )
}
