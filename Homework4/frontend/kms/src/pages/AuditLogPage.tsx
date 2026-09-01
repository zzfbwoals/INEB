import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import AppLayout from '@/components/layout/AppLayout'
import { AUDIT_ACTIONS, downloadAuditCsv, listAuditLogs, verifyAuditChain, type AuditLogItem, type AuditVerifyResult } from '@/api/audit'
import type { PageResponse } from '@/api/keys'
import { Button } from '@/components/ui/button'
import { errorMessage, useToast } from '@/components/ui/toast'

const PAGE_SIZE = 20

/* 목업 audit.html — 감사 로그. append-only 해시 체인 + 재검증 (CSV 내려받기는 4주차) */
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
  const [verify, setVerify] = useState<AuditVerifyResult | null>(null)
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
      setVerify(result)
      toast(message ?? '검증이 완료되었습니다.', result.valid ? 'ok' : 'error')
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
          <Button variant="ghost" disabled={verifying} onClick={runVerify}>{verifying ? '검증 중…' : '체인 재검증'}</Button>
          <Button onClick={async () => {
            try {
              await downloadAuditCsv({ actor, action, target, from, to })
              toast('CSV 다운로드를 시작합니다. 내려받기도 감사로그에 기록됩니다.')
            } catch (err) {
              toast(errorMessage(err), 'error')
            }
          }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 4v11m0 0 4.5-4.5M12 15l-4.5-4.5M4 19h16" /></svg>
            CSV 내려받기
          </Button>
        </div>
      </div>

      {verify && (
        <div className={`verify-band ${verify.valid ? '' : 'bad'}`}>
          <span className="ic">
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 3 4.5 6v5c0 5 3.2 8.6 7.5 10 4.3-1.4 7.5-5 7.5-10V6L12 3z" />
              {verify.valid ? <path d="m9 11.5 2.2 2.2L15.5 9" /> : <path d="M12 8v5m0 3v.01" />}
            </svg>
          </span>
          <div>
            <b>{verify.valid ? '해시 체인 무결성 검증 통과' : `해시 체인 위반 ${verify.violations.length}개 구간 감지`}</b><br />
            <span>
              {verify.totalRows.toLocaleString()}건 전체 순차 검증
              {verify.valid
                ? ' · 삭제/삽입/변조 구간 없음'
                : ' · ' + verify.violations.map((v) =>
                    `#${v.fromId}${v.toId !== v.fromId ? `~#${v.toId}` : ''} ${v.type === 'TAMPERED' ? '행 변조' : '체인 단절(삭제·삽입)'}`).join(' · ')}
              {' · '}{verify.verifiedAt} KST
            </span>
          </div>
        </div>
      )}

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
          <table>
            <thead>
              <tr><th>ID</th><th>일시 (KST)</th><th>행위자</th><th>행위</th><th>대상</th><th>상세</th></tr>
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
                  <td className="mono" style={{ color: 'var(--text-3)', maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={a.detail}>{a.detail}</td>
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
