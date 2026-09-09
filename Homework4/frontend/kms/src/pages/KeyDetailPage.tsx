import { Ban, ChevronDown, Download, FlaskConical, Pencil, Play, Power, RefreshCw, ShieldCheck, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import AppLayout from '@/components/layout/AppLayout'
import { getHistory, getKey, getUsage, type HistoryItem, type KeyDetail, type UsageResponse, type VersionInfo } from '@/api/keys'
import { ALGOS, PURPOSE_KO, TRIGGER_KO, canEncrypt, canSign } from '@/lib/keyRules'
import { abbreviatePem, dday, downloadText, fmt, relTime } from '@/lib/format'
import { subscribeUiEvents } from '@/lib/events'
import { Button } from '@/components/ui/button'
import { Dialog, DialogBody, DialogContent, DialogFooter } from '@/components/ui/dialog'
import { errorMessage, useToast } from '@/components/ui/toast'
import { CopyButton } from '@/components/ui/copy'
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
  const [tlModal, setTlModal] = useState(false)
  const [tlMax, setTlMax] = useState<number | null>(null)
  const [tlOverflow, setTlOverflow] = useState(false)
  const metaCardRef = useRef<HTMLDivElement>(null)
  const tlCardRef = useRef<HTMLDivElement>(null)
  const bottomCardRef = useRef<HTMLDivElement>(null)
  const [tblMax, setTblMax] = useState<number | null>(null)

  // 타임라인 카드는 왼쪽 메타 카드 높이까지만 — 넘치면 하단 페이드 + 더보기(모달). 1열 레이아웃(<=1100px)에서는 제한하지 않는다.
  useLayoutEffect(() => {
    function measure() {
      const meta = metaCardRef.current
      const card = tlCardRef.current
      if (!meta || !card || window.innerWidth <= 1100) {
        setTlMax(null)
        setTlOverflow(false)
        return
      }
      const h = meta.offsetHeight
      setTlMax(h)
      setTlOverflow(card.scrollHeight > h + 1)
    }
    measure()
    window.addEventListener('resize', measure)
    return () => window.removeEventListener('resize', measure)
  })

  // 버전 목록·사용 이력 카드는 뷰포트 하단까지만 — 행이 많으면 페이지 대신 카드 내부(tbl-wrap)가 스크롤된다.
  // 남은 높이 = 뷰포트 − 표 상단 − 카드 하단 테두리(1) − .content 하단 여백(60). 최소 180px(헤더+3행)는 확보하고, 1열 레이아웃(<=1100px)에서는 제한하지 않는다.
  useLayoutEffect(() => {
    function fit() {
      const wrap = bottomCardRef.current?.querySelector<HTMLDivElement>('.tbl-wrap')
      if (!wrap || window.innerWidth <= 1100) {
        setTblMax(null)
        return
      }
      const top = wrap.getBoundingClientRect().top + window.scrollY
      setTblMax(Math.max(180, Math.floor(window.innerHeight - top - 61)))
    }
    fit()
    window.addEventListener('resize', fit)
    return () => window.removeEventListener('resize', fit)
  })

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

  // 실시간 갱신 — 이 키를 대상으로 한 행위(테스트·상태 변경·스케줄러 등)가 커밋되면 즉시 refetch
  useEffect(() => {
    return subscribeUiEvents((e) => {
      if (e.target === `KEY#${keyUid}`) load()
    })
  }, [keyUid, load])

  // 상대시간("방금 전"→"1분 전")은 시간이 흐르면 달라지므로 표시 텍스트를 상태로 두고 30초마다 재계산
  // (React Compiler가 relTime(lastUse)를 입력값 기준으로 메모이즈하므로 재렌더만으로는 갱신되지 않는다)
  const lastUse = detail?.versions.map((v) => v.lastUsedAt).filter((x): x is string => !!x).sort().pop() ?? null
  const [lastUseText, setLastUseText] = useState('—')
  useEffect(() => {
    const update = () => setLastUseText(relTime(lastUse))
    update()
    const t = setInterval(update, 30_000)
    return () => clearInterval(t)
  }, [lastUse])

  if (!detail) {
    return <AppLayout><div className="help">불러오는 중…</div></AppLayout>
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
    <AppLayout>
      <div className="page-h">
        <div>
          <div className="hdr-row">
            <h2>{detail.keyName}</h2>
            <StateBadge state={s} />
            <span className="vtag cur">v{detail.currentVersion}</span>
          </div>
          <div className="desc mono" style={{ marginTop: 8, display: 'flex', alignItems: 'center', gap: 6, minHeight: 26 }}>
            <span>{detail.keyUid}</span>
            <CopyButton text={detail.keyUid} label="UID 복사" />
          </div>
        </div>
        {/* 헤더 액션 — 배경 없는 아이콘 버튼 + 툴팁 (Button 의 hover filter 가 스택 컨텍스트를 만들어 툴팁이 옆 카드에 가려지므로 .icon-btn 사용) */}
        <div className="acts icon-acts">
          <Link className="icon-btn" data-tip="감사 로그" aria-label="감사 로그" to={`/audit?target=${encodeURIComponent(`KEY#${detail.keyUid}`)}`}><ShieldCheck size={17} /></Link>
          {s !== 'DESTROYED' && <Link className="icon-btn" data-tip="동작 테스트" aria-label="동작 테스트" to={`/keys/test?id=${detail.keyUid}`}><FlaskConical size={17} /></Link>}
          {pre && <button type="button" className="icon-btn primary" data-tip="활성화" aria-label="활성화" onClick={() => setAction({ kind: 'ACTIVATE', version: pre.version })}><Play size={17} /></button>}
          {actives.length > 0 && <button type="button" className="icon-btn" data-tip="정지" aria-label="정지" onClick={() => setAction({ kind: 'DEACTIVATE', version: null })}><Ban size={17} /></button>}
          {(s === 'ACTIVE' || s === 'DEACTIVATED') && <button type="button" className="icon-btn primary" data-tip="갱신" aria-label="갱신" onClick={() => setAction({ kind: 'ROTATE' })}><RefreshCw size={17} /></button>}
          {s !== 'DESTROYED' && destroyable && <button type="button" className="icon-btn danger" data-tip="삭제" aria-label="삭제" onClick={() => setAction({ kind: 'DESTROY', version: null })}><Trash2 size={17} /></button>}
        </div>
      </div>

      <div className="detail-grid">
        <div className="card" ref={metaCardRef}>
          <div className="card-h">
            <h3>키 메타정보</h3>
            {s !== 'DESTROYED' && (
              <button type="button" className="icon-btn" data-tip="수정" aria-label="수정" onClick={() => setEditOpen(true)}>
                <Pencil size={15} />
              </button>
            )}
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
            <Meta k="integrity_hash" mono v={<span style={{ fontSize: 11.5, color: 'var(--text-3)' }}>{detail.integrityHashShort ?? '—'} <b style={{ color: detail.integrityValid ? 'var(--green)' : 'var(--red)' }}>{detail.integrityValid ? '✓' : '✕'}</b></span>} />
            {detail.publicKeyPem && (
              <div className="meta-it full">
                <div className="k" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  공개키 (v{detail.currentVersion}, PEM)
                  <CopyButton text={detail.publicKeyPem} />
                  <button type="button" className="icon-btn sm" data-tip=".pem 다운로드" aria-label=".pem 다운로드"
                    onClick={() => downloadText(`${detail.keyName}_v${detail.currentVersion}.pem`, detail.publicKeyPem!, 'application/x-pem-file')}>
                    <Download size={13} />
                  </button>
                </div>
                <div className="pubkey" title="전문은 복사 또는 .pem 다운로드로 확인">{abbreviatePem(detail.publicKeyPem)}</div>
              </div>
            )}
          </div>
          <div className="usage-mini">
            <div><div className="k">마지막 사용</div><div className="v" style={{ fontSize: 15 }} title={lastUse ?? ''}>{lastUseText}</div></div>
            <div><div className="k">테스트 호출</div><div className="v">{stats.total}</div></div>
            <div><div className="k">{opL}</div><div className="v">{enc ? stats.encrypt : stats.sign}</div></div>
            <div><div className="k">{opR}</div><div className="v">{enc ? stats.decrypt : stats.verify}</div></div>
            <div><div className="k">구 버전 {opR}</div><div className="v" style={{ color: 'var(--text-2)' }}>{stats.oldVersion}</div></div>
            <div><div className="k">실패·차단</div><div className="v" style={{ color: 'var(--red)' }}>{stats.failed}</div></div>
          </div>
        </div>

        <div className="card tl-card" ref={tlCardRef} style={tlMax !== null ? { maxHeight: tlMax } : undefined}>
          <div className="card-h"><h3>상태 변경 타임라인</h3></div>
          <div className="timeline"><TimelineItems history={history} /></div>
          {tlOverflow && (
            <div className="tl-more">
              {/* 카드가 overflow:hidden 이라 툴팁은 위로(tip-up) */}
              <button type="button" className="icon-btn tip-up" data-tip="더보기" aria-label="더보기" onClick={() => setTlModal(true)}>
                <ChevronDown size={16} />
              </button>
            </div>
          )}
        </div>
      </div>

      <div className="card" ref={bottomCardRef}>
        <div className="tabs">
          <button type="button" className={`tab ${tab === 'ver' ? 'on' : ''}`} onClick={() => setTab('ver')}>버전 목록</button>
          <button type="button" className={`tab ${tab === 'usage' ? 'on' : ''}`} onClick={() => setTab('usage')}>사용 이력</button>
        </div>
        {tab === 'ver' ? (
          <div className="tbl-wrap tbl-scroll" style={tblMax !== null ? { maxHeight: tblMax } : undefined}>
            <table>
              <thead><tr><th>버전</th><th>상태</th><th>활성일</th><th>마지막 사용</th><th>사용 횟수</th><th>{capLabel}</th><th>무결성</th><th></th></tr></thead>
              <tbody>
                {detail.versions.map((v) => <VersionRow key={v.version} v={v} detail={detail} onAction={setAction} onReveal={setRevealVersion} />)}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="tbl-wrap tbl-scroll" style={tblMax !== null ? { maxHeight: tblMax } : undefined}>
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

      {/* 전체 타임라인 — 페이지 대신 모달 본문이 스크롤된다 */}
      <Dialog open={tlModal} onOpenChange={(o) => !o && setTlModal(false)}>
        <DialogContent title="상태 변경 타임라인">
          <DialogBody style={{ maxHeight: '62vh', overflowY: 'auto' }}>
            <div className="timeline" style={{ padding: 0 }}><TimelineItems history={history} /></div>
          </DialogBody>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setTlModal(false)}>닫기</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <KeyActionDialogs detail={detail} state={action} onClose={() => setAction(null)} onDone={load} />
      {editOpen && <KeyEditDialog detail={detail} open onClose={() => setEditOpen(false)} onDone={load} />}
      {revealVersion !== null && <KeyRevealDialog detail={detail} version={revealVersion} onClose={() => setRevealVersion(null)} />}
    </AppLayout>
  )
}

function TimelineItems({ history }: { history: HistoryItem[] }) {
  return (
    <>
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
    </>
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
  const role = canEncrypt(detail.purpose) ? (canSign(detail.purpose) ? '복호화·검증' : '복호화') : '검증'
  const cap = v.state !== 'ACTIVE' ? '✗ / ✗' : v.canEncrypt ? '✓ / ✓'
    : <>✗ / ✓<span className="roletag">{role} 전용</span></>
  /* 버전별 액션 — 아이콘 버튼 + 툴팁. 표가 내부 스크롤(overflow)이라 아래·위 툴팁이 잘리므로 왼쪽(tip-left)으로 띄운다 */
  const destroy = <button type="button" className="icon-btn danger tip-left" data-tip="삭제" aria-label="삭제" onClick={() => onAction({ kind: 'DESTROY', version: v.version })}><Trash2 size={15} /></button>
  let act: React.ReactNode = null
  if (v.state === 'PRE_ACTIVE') act = <>
    <button type="button" className="icon-btn tip-left" data-tip="활성화" aria-label="활성화" onClick={() => onAction({ kind: 'ACTIVATE', version: v.version })}><Play size={15} /></button>
    {destroy}
  </>
  else if (v.state === 'ACTIVE') act = isLatest
    ? <span className="help" title="최신 버전은 단독 정지 불가 — 키 정지 또는 갱신 후 정지">최신 버전</span>
    : <button type="button" className="icon-btn tip-left" data-tip="정지" aria-label="정지" onClick={() => onAction({ kind: 'DEACTIVATE', version: v.version })}><Ban size={15} /></button>
  else if (v.state === 'DEACTIVATED') act = <>
    {v.deactivationTrigger === 'INTEGRITY' && <button type="button" className="icon-btn primary tip-left" data-tip="재활성화" aria-label="재활성화" onClick={() => onAction({ kind: 'REACTIVATE', version: v.version })}><Power size={15} /></button>}
    {destroy}
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
      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }} title="" onClick={(e) => e.stopPropagation()}>{act}</td>
    </tr>
  )
}
