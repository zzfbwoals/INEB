import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import AppLayout from '@/components/layout/AppLayout'
import { getHistory, getKey, getUsage, type HistoryItem, type KeyDetail, type UsageResponse, type VersionInfo } from '@/api/keys'
import { ALGOS, PURPOSE_KO, TRIGGER_KO, canEncrypt, canSign } from '@/lib/keyRules'
import { dday, fmt } from '@/lib/format'
import { Button } from '@/components/ui/button'
import { errorMessage, useToast } from '@/components/ui/toast'
import { IntegrityBadge, StateBadge } from '@/components/keys/StateBadge'
import { KeyActionDialogs, type ActionDialogState } from '@/components/keys/KeyActionDialogs'
import { KeyEditDialog } from '@/components/keys/KeyEditDialog'
import { KeyRevealDialog } from '@/components/keys/KeyRevealDialog'

/* 목업 key-detail.html — 키 상세 */
export default function KeyDetailPage() {
  const { keyUid = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [detail, setDetail] = useState<KeyDetail | null>(null)
  const [history, setHistory] = useState<HistoryItem[]>([])
  const [usage, setUsage] = useState<UsageResponse | null>(null)
  const [tab, setTab] = useState<'ver' | 'usage'>('ver')
  const [action, setAction] = useState<ActionDialogState>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [revealVersion, setRevealVersion] = useState<number | null>(null)

  const load = useCallback(async () => {
    try {
      const [d, h, u] = await Promise.all([getKey(keyUid), getHistory(keyUid), getUsage(keyUid)])
      setDetail(d); setHistory(h); setUsage(u)
    } catch (err) {
      toast(errorMessage(err), 'error')
      navigate('/keys', { replace: true })
    }
  }, [keyUid, navigate, toast])

  useEffect(() => { load() }, [load])

  if (!detail) {
    return <AppLayout crumb="키 관리 / 키 목록 / 키 상세"><div className="help">불러오는 중…</div></AppLayout>
  }

  const s = detail.status
  const pre = detail.versions.find((v) => v.state === 'PRE_ACTIVE')
  const actives = detail.versions.filter((v) => v.state === 'ACTIVE')
  const current = detail.versions.find((v) => v.version === detail.currentVersion)
  const destroyable = detail.versions.some((v) => v.state === 'DEACTIVATED' || v.state === 'PRE_ACTIVE')
  const enc = canEncrypt(detail.purpose), sig = canSign(detail.purpose)
  const opL = enc ? '암호화' : '서명', opR = enc ? '복호화' : '검증'
  const capLabel = enc && sig ? '암호화·서명 / 복호화·검증' : enc ? '암호화 / 복호화' : '서명 / 검증'
  const rule = ALGOS[detail.algorithm]
  const d = dday(detail.nextRotationAt)
  const stats = usage?.stats ?? detail.usageStats

  return (
    <AppLayout crumb="키 관리 / 키 목록 / 키 상세">
      <div className="page-h">
        <div>
          <div className="hdr-row">
            <Button asChild variant="ghost" size="sm"><Link to="/keys">← 목록</Link></Button>
            <h2>{detail.keyName}</h2>
            <StateBadge state={s} />
            <span className="vtag cur">v{detail.currentVersion}</span>
          </div>
          <div className="desc mono" style={{ marginTop: 8 }}>{detail.keyUid}</div>
        </div>
        <div className="acts">
          {s !== 'DESTROYED' && <Button asChild variant="ghost"><Link to={`/keys/test?id=${detail.keyUid}`}>동작 테스트</Link></Button>}
          {pre && <Button onClick={() => setAction({ kind: 'ACTIVATE', version: pre.version })}>활성화</Button>}
          {actives.length > 0 && <Button variant="ghost" onClick={() => setAction({ kind: 'DEACTIVATE', version: null })}>정지</Button>}
          {(s === 'ACTIVE' || s === 'DEACTIVATED') && <Button onClick={() => setAction({ kind: 'ROTATE' })}>갱신</Button>}
          {s !== 'DESTROYED' && destroyable && <Button variant="danger" onClick={() => setAction({ kind: 'DESTROY', version: null })}>삭제</Button>}
        </div>
      </div>

      <div className="detail-grid">
        <div className="card">
          <div className="card-h">
            <h3>키 메타정보</h3>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              {detail.integrityValid ? <span className="badge b-ok">무결성 검증 통과</span> : <span className="badge b-bad">무결성 위반 감지</span>}
              {s !== 'DESTROYED' && <Button variant="ghost" size="sm" onClick={() => setEditOpen(true)}>수정</Button>}
            </div>
          </div>
          <div className="meta-grid">
            <Meta k="알고리즘 / 사이즈" v={`${detail.algorithm} · ${rule.sizeLabel ? rule.sizeLabel(detail.keySize) : detail.keySize + ' bit'}`} />
            <Meta k="모드 / 용도" v={`${detail.mode ?? (rule.kind === 'HMAC' ? 'HMAC' : '—')} · ${PURPOSE_KO[detail.purpose]}`} />
            <Meta k="버전" v={`v${detail.currentVersion}`} />
            <Meta k="갱신 주기 / 다음 갱신" mono v={detail.autoRotate
              ? <>{detail.rotationPeriodDays}일 · {fmt(detail.nextRotationAt)}{d !== null && d <= 0 && s === 'ACTIVE' && <b style={{ color: 'var(--red)' }}> (지연)</b>}</>
              : '수동 갱신'} />
            <Meta k={`활성일 (v${detail.currentVersion})`} mono v={<>{fmt(current?.activationDate)}{current?.state === 'PRE_ACTIVE' && <b style={{ color: 'var(--blue)' }}> (예정)</b>}</>} />
            <Meta k="설명" v={<span style={{ fontWeight: 500 }}>{detail.description ?? '—'}</span>} />
            <Meta k="생성일" mono v={detail.createdAt} />
            <Meta k="integrity_hash" mono v={<span style={{ fontSize: 11.5, color: detail.integrityValid ? 'var(--text-3)' : 'var(--red)' }}>{detail.integrityHashShort ?? '—'} {detail.integrityValid ? '✓' : '✕ 불일치'}</span>} />
            {detail.publicKeyPem && (
              <div className="meta-it full">
                <div className="k">공개키 (v{detail.currentVersion}, PEM)</div>
                <div className="pubkey">{detail.publicKeyPem}</div>
              </div>
            )}
          </div>
          <div className="usage-mini">
            <div><div className="k">테스트 호출(30일)</div><div className="v">{stats.total}</div></div>
            <div><div className="k">{opL}</div><div className="v">{enc ? stats.encrypt : stats.sign}</div></div>
            <div><div className="k">{opR}</div><div className="v">{enc ? stats.decrypt : stats.verify}</div></div>
            <div><div className="k">구 버전 {opR}</div><div className="v" style={{ color: 'var(--text-2)' }}>{stats.oldVersion}</div></div>
            <div><div className="k">실패·차단</div><div className="v" style={{ color: 'var(--red)' }}>{stats.failed}</div></div>
          </div>
        </div>

        <div className="card">
          <div className="card-h"><h3>상태 변경 타임라인</h3></div>
          <div className="timeline">
            {history.length === 0 && <div className="help" style={{ padding: '12px 0' }}>이력이 없습니다</div>}
            {history.map((h, i) => (
              <div key={i} className="tl-it">
                <span className={`tl-dot ${h.trigger === 'INTEGRITY' ? 'bad' : i === 0 ? 'now' : ''}`} />
                <div className="tl-body">
                  <b><span className="vtag">v{h.version}</span> {h.fromState ? `${h.fromState} → ` : '생성 → '}{h.toState}</b>
                  <span className={`trg ${h.trigger === 'INTEGRITY' ? 'bad' : h.trigger === 'DATE_REACHED' || h.trigger === 'SCHEDULE' ? 'sys' : h.trigger === 'REACTIVATE' ? 'ok' : ''}`}>{TRIGGER_KO[h.trigger]}</span>
                  <div className="rs">사유: {h.reason}</div>
                  <div className="at">{h.changedAt} · {h.changedBy}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="card">
        <div className="tabs">
          <button type="button" className={`tab ${tab === 'ver' ? 'on' : ''}`} onClick={() => setTab('ver')}>버전 목록</button>
          <button type="button" className={`tab ${tab === 'usage' ? 'on' : ''}`} onClick={() => setTab('usage')}>사용 이력</button>
        </div>
        {tab === 'ver' ? (
          <div className="tbl-wrap">
            <table>
              <thead><tr><th>버전</th><th>상태</th><th>활성일</th><th>마지막 사용</th><th>사용 횟수</th><th>{capLabel}</th><th>무결성</th><th></th></tr></thead>
              <tbody>
                {detail.versions.map((v) => <VersionRow key={v.version} v={v} detail={detail} onAction={setAction} onReveal={setRevealVersion} />)}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="tbl-wrap">
            <table>
              <thead><tr><th>일시</th><th>연산</th><th>버전</th><th>결과</th><th>실패 사유 / 비고</th></tr></thead>
              <tbody>
                {(usage?.logs.content.length ?? 0) === 0 && <tr><td colSpan={5} className="tbl-empty" style={{ padding: 28 }}>이 키에 대한 사용 기록이 없습니다</td></tr>}
                {usage?.logs.content.map((u, i) => (
                  <tr key={i}>
                    <td className="mono">{u.usedAt}</td>
                    <td className="mono">{u.operation}</td>
                    <td><span className="vtag">v{u.version}</span>{u.oldVersion && <> <span className="help">구 버전</span></>}</td>
                    <td>{u.result === 'SUCCESS' ? <span className="badge b-ok">성공</span> : <span className="badge b-bad">실패</span>}</td>
                    <td className="mono" style={{ color: u.failReason ? 'var(--red)' : 'var(--text-3)', fontSize: 11.5 }}>{u.failReason ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <KeyActionDialogs detail={detail} state={action} onClose={() => setAction(null)} onDone={load} />
      {editOpen && <KeyEditDialog detail={detail} open onClose={() => setEditOpen(false)} onDone={load} />}
      {revealVersion !== null && <KeyRevealDialog detail={detail} version={revealVersion} onClose={() => setRevealVersion(null)} />}
    </AppLayout>
  )
}

function Meta({ k, v, mono }: { k: string; v: React.ReactNode; mono?: boolean }) {
  return (
    <div className="meta-it">
      <div className="k">{k}</div>
      <div className={`v ${mono ? 'mono' : ''}`}>{v}</div>
    </div>
  )
}

function VersionRow({ v, detail, onAction, onReveal }: { v: VersionInfo; detail: KeyDetail; onAction: (a: ActionDialogState) => void; onReveal: (version: number) => void }) {
  const isCur = v.version === detail.currentVersion
  const isLatest = v.version === Math.max(...detail.versions.map((x) => x.version))
  const cap = v.state !== 'ACTIVE' ? '✗ / ✗' : v.canEncrypt ? '✓ / ✓' : '✗ / ✓'
  let act: React.ReactNode = null
  if (v.state === 'PRE_ACTIVE') act = <><Button variant="ghost" size="sm" onClick={() => onAction({ kind: 'ACTIVATE', version: v.version })}>활성화</Button> <Button variant="ghost" size="sm" onClick={() => onAction({ kind: 'DESTROY', version: v.version })}>삭제</Button></>
  else if (v.state === 'ACTIVE') act = isLatest
    ? <span className="help" title="최신 버전은 단독 정지 불가 — 키 정지 또는 갱신 후 정지">최신 버전</span>
    : <Button variant="ghost" size="sm" title="정지 시 이 버전의 복호화·검증이 차단됩니다" onClick={() => onAction({ kind: 'DEACTIVATE', version: v.version })}>정지</Button>
  else if (v.state === 'DEACTIVATED') act = <>
    {v.deactivationTrigger === 'INTEGRITY' && <><Button size="sm" onClick={() => onAction({ kind: 'REACTIVATE', version: v.version })}>재활성화</Button> </>}
    <Button variant="ghost" size="sm" onClick={() => onAction({ kind: 'DESTROY', version: v.version })}>삭제</Button>
  </>
  const revealable = v.state !== 'DESTROYED'
  return (
    <tr className={`${isCur ? 'vcur' : ''} ${revealable ? 'rowlink' : ''}`.trim()}
        title={revealable ? '클릭하여 키값 조회 — 사유 필수 · 감사로그 기록' : undefined}
        onClick={revealable ? () => onReveal(v.version) : undefined}>
      <td><span className={`vtag ${isCur ? 'cur' : ''}`}>v{v.version}</span></td>
      <td><StateBadge state={v.state} /></td>
      <td className="mono">
        {fmt(v.activationDate)}
        {v.state === 'PRE_ACTIVE' && <span style={{ color: 'var(--blue)' }}> 예정</span>}
        {v.state === 'DESTROYED' && <span style={{ color: 'var(--text-3)' }}> · 폐기 {fmt(v.destroyedAt)}</span>}
      </td>
      <td className="mono">{fmt(v.lastUsedAt)}</td>
      <td className="mono">{v.usageCount.toLocaleString()}</td>
      <td className="mono" style={{ color: 'var(--text-2)' }}>{cap}</td>
      <td>{v.state === 'DESTROYED' ? <span style={{ color: 'var(--text-3)' }}>—</span> : <IntegrityBadge valid={v.integrityValid} />}</td>
      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>{act}</td>
    </tr>
  )
}
