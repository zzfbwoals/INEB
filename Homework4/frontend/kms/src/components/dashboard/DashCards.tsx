import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router'
import { Area, CartesianGrid, ComposedChart, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { AlgoCount, DashboardSummary, ExpiringItem, Failure, Signal, TrendDays, TrendOp, UsageTrend } from '@/api/dashboard'
import type { AuditLogItem } from '@/api/audit'
import type { KeyState } from '@/api/keys'
import { STATE_KO } from '@/lib/keyRules'
import { relTime } from '@/lib/format'
import { CardHead } from './PanelBoard'

/* 대시보드 카드 10종 — 목업 dashboard.html 의 각 카드를 1:1 로 옮김. 데이터는 페이지가 내려주고 SSE 수신 시 페이지가 refetch 한다 */

const ORDER: KeyState[] = ['ACTIVE', 'PRE_ACTIVE', 'DEACTIVATED', 'DESTROYED']
const COLOR: Record<KeyState, string> = { ACTIVE: 'var(--green)', PRE_ACTIVE: 'var(--blue)', DEACTIVATED: 'var(--text-2)', DESTROYED: 'var(--text-3)' }

/* ---------- 요약: 전체 KMS 키 (도넛 — 카드 본문 크기에 맞춰 유동, 구간 호버 시 칩 강조 + 툴팁) ---------- */
export function StatKeys({ s }: { s: DashboardSummary['keys'] | null }) {
  const rowRef = useRef<HTMLDivElement>(null)
  const [size, setSize] = useState(64)
  const [hv, setHv] = useState<KeyState | null>(null)
  useEffect(() => {
    const row = rowRef.current
    if (!row) return
    const fit = () => setSize(Math.round(Math.max(44, Math.min(200, row.clientHeight, row.clientWidth * 0.4))))
    const ro = new ResizeObserver(fit)
    ro.observe(row)
    fit()
    return () => ro.disconnect()
  }, [])
  const total = s?.total ?? 0
  const cnt = (st: KeyState) => s?.byStatus[st] ?? 0
  const R = 26, C = 2 * Math.PI * R
  let off = 0
  const arcs = ORDER.map((st) => {
    const f = total ? cnt(st) / total : 0
    const a = { st, f, off }
    off += f
    return a
  }).filter((a) => a.f > 0)
  const tip = hv ? `${STATE_KO[hv]} ${cnt(hv)}건 · ${total ? Math.round((cnt(hv) / total) * 100) : 0}%` : `전체 ${total}건`
  // 툴팁은 카드(.stat overflow:hidden) 안에 갇혀 잘리므로 body 에 포털로 띄우고 도넛 위 고정 좌표에 둔다
  const wrapRef = useRef<HTMLSpanElement>(null)
  const [tipPos, setTipPos] = useState<{ x: number; y: number } | null>(null)
  const showTip = () => {
    const r = wrapRef.current?.getBoundingClientRect()
    if (r) setTipPos({ x: r.left + r.width / 2, y: r.top - 8 })
  }
  return (
    <>
      <CardHead id="keys" title={<Link className="tl" to="/keys">전체 KMS 키</Link>} />
      <div className="stat-b">
        <div className="row" ref={rowRef}>
          <span className="donut-wrap" ref={wrapRef} onMouseEnter={showTip} onMouseLeave={() => { setTipPos(null); setHv(null) }}>
            {tipPos && createPortal(<div className="donut-tip" style={{ left: tipPos.x, top: tipPos.y }}>{tip}</div>, document.body)}
            <svg className={`donut ${hv ? 'hv' : ''}`} width={size} height={size} viewBox="0 0 64 64" onMouseLeave={() => setHv(null)}>
              {arcs.map((a) => (
                <circle
                  key={a.st}
                  className={hv === a.st ? 'on' : ''}
                  cx="32" cy="32" r={R} fill="none" stroke={COLOR[a.st]} strokeWidth="9"
                  strokeDasharray={`${a.f * C - 1.5} ${C - a.f * C + 1.5}`} strokeDashoffset={-a.off * C}
                  transform="rotate(-90 32 32)"
                  onMouseEnter={() => setHv(a.st)}
                />
              ))}
            </svg>
          </span>
          <div style={{ minWidth: 0 }}>
            <div className="num">{total}</div>
            <div className="chips" style={{ marginTop: 4 }}>
              {ORDER.map((st) => (
                <span key={st} className={hv === st ? 'on' : ''}><i style={{ background: COLOR[st] }} />{STATE_KO[st]} <b>{cnt(st)}</b></span>
              ))}
            </div>
          </div>
        </div>
        <div className="delta">버전 <b>{s?.versions ?? 0}</b> · 복호화 전용 <b>{s?.decryptOnly ?? 0}</b> · 예약 활성 <b>{s?.scheduled ?? 0}</b> · 폐기 대기 <b>{s?.destroyPending ?? 0}</b></div>
      </div>
    </>
  )
}

export function StatUsers({ s }: { s: DashboardSummary['users'] | null }) {
  return (
    <>
      <CardHead id="users" title={<Link className="tl" to="/users">서비스 사용자</Link>} />
      <div className="stat-b">
        <div className="num">{s?.total ?? 0}</div>
        <div className="delta">활성 <b>{s?.active ?? 0}</b> · 정지 <b>{s?.suspended ?? 0}</b> · 최근 30일 가입 <b>{s?.joined30d ?? 0}</b></div>
      </div>
    </>
  )
}

export function StatNotices({ s }: { s: DashboardSummary['notices'] | null }) {
  return (
    <>
      <CardHead id="notices" title={<Link className="tl" to="/notices">공지사항</Link>} />
      <div className="stat-b">
        <div className="num">{s?.total ?? 0}</div>
        <div className="delta">고정 <b>{s?.pinned ?? 0}</b> · 이번 달 <b>{s?.thisMonth ?? 0}</b> · 첨부 <b>{s?.files ?? 0}</b>건 암호화 저장</div>
      </div>
    </>
  )
}

/* 무결성 위반 — 키 메타 / 키 버전 / 사용자 / 감사 체인. 0건이면 강조 없음 */
export function StatIntegrity({ s }: { s: DashboardSummary['integrity'] | null }) {
  const total = s?.total ?? 0
  const cell = (label: string, v: number) => <span key={label}>{label} <b className={v ? 'bad' : ''}>{v}</b></span>
  return (
    <>
      <CardHead id="integrity" title="무결성 위반" />
      <div className="stat-b">
        <div className="num" style={total ? { color: 'var(--red)' } : undefined}>{total}</div>
        <div className="iv-grid">
          {cell('키 메타', s?.keyMeta ?? 0)}{cell('키 버전', s?.keyVersion ?? 0)}{cell('사용자', s?.user ?? 0)}{cell('감사 체인', s?.auditChain ?? 0)}
        </div>
      </div>
    </>
  )
}

/* ---------- 키 사용 추이 (Recharts) ---------- */
function TrendTip({ active, payload, label }: { active?: boolean; payload?: { value: number }[]; label?: string }) {
  if (!active || !payload?.length || !label) return null
  const d = new Date(label + 'T00:00:00+09:00')
  return (
    <div className="chart-tip">
      <b>{label} ({'일월화수목금토'[d.getDay()]})</b>
      <span><i style={{ background: 'var(--blue)' }} />성공 {payload[0]?.value ?? 0}건</span>
      <span><i style={{ background: 'var(--red)' }} />실패 {payload[1]?.value ?? 0}건</span>
    </div>
  )
}

export function TrendCard({ trend, op, days, onOp, onDays }: {
  trend: UsageTrend | null; op: TrendOp; days: TrendDays; onOp: (o: TrendOp) => void; onDays: (d: TrendDays) => void
}) {
  const pts = trend?.points ?? []
  const tot = pts.reduce((a, r) => a + r.ok + r.fail, 0)
  const fails = pts.reduce((a, r) => a + r.fail, 0)
  return (
    <>
      <CardHead
        id="trend"
        title="키 사용 추이"
        right={(
          <>
            <div className="seg">
              {(['ALL', 'ENC', 'SIGN'] as TrendOp[]).map((o) => (
                <button key={o} type="button" className={op === o ? 'on' : ''} onClick={() => onOp(o)}>{o === 'ALL' ? '전체' : o === 'ENC' ? '암복호화' : '서명검증'}</button>
              ))}
            </div>
            <div className="seg">
              {([7, 30] as TrendDays[]).map((d) => (
                <button key={d} type="button" className={days === d ? 'on' : ''} onClick={() => onDays(d)}>{d}일</button>
              ))}
            </div>
          </>
        )}
      />
      <div className="chart-sum">
        <span>총 호출 <b>{tot.toLocaleString()}</b></span>
        <span>실패 <b style={{ color: fails ? 'var(--red)' : 'inherit' }}>{fails}</b></span>
        <span>실패율 <b>{tot ? ((fails / tot) * 100).toFixed(1) : 0}%</b></span>
        <span>일평균 <b>{pts.length ? (tot / pts.length).toFixed(1) : 0}</b></span>
      </div>
      <div className="chart-wrap">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={pts} margin={{ top: 8, right: 14, left: 0, bottom: 0 }}>
            <CartesianGrid vertical={false} stroke="var(--line-strong)" />
            <XAxis dataKey="date" tickFormatter={(v: string) => v.slice(5).replace('-', '/')} tick={{ fill: 'var(--text-3)', fontSize: 10 }} axisLine={false} tickLine={false} interval={days === 7 ? 0 : 4} />
            <YAxis width={30} tick={{ fill: 'var(--text-3)', fontSize: 10 }} axisLine={false} tickLine={false} allowDecimals={false} />
            <Tooltip content={<TrendTip />} cursor={{ stroke: 'var(--line-strong)' }} isAnimationActive={false} />
            <Area type="linear" dataKey="ok" stroke="var(--blue)" fill="var(--blue)" fillOpacity={0.1} strokeWidth={2} dot={false} activeDot={{ r: 3 }} isAnimationActive={false} />
            <Line type="linear" dataKey="fail" stroke="var(--red)" strokeWidth={1.8} dot={false} activeDot={{ r: 3 }} isAnimationActive={false} />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      <div className="legend"><span><i style={{ background: 'var(--blue)' }} />성공</span><span><i style={{ background: 'var(--red)' }} />실패</span></div>
    </>
  )
}

/* ---------- 갱신 임박 · 예약 활성 ---------- */
export function ExpiringCard({ items }: { items: ExpiringItem[] }) {
  return (
    <>
      <CardHead id="expiring" title={<>갱신 임박 · 예약 활성 <span className="cnt">{items.length}</span></>} right={<span className="hint">30일 이내</span>} />
      <div className="body">
        {items.length ? items.map((it) => (
          <Link key={`${it.keyUid}-${it.kind}-${it.version}`} className="exp-it" to={`/keys/${it.keyUid}`}>
            <span className="kind">{it.kind === 'ACTIVATION' ? '활성' : '갱신'}</span>
            <div className="kn"><b>{it.keyName}</b><span>{it.algorithm}-{it.keySize} · v{it.version} · {it.at.slice(0, 10)} {it.kind === 'ACTIVATION' ? '활성 예정' : '갱신 예정'}</span></div>
            <span className={`dday ${it.dday <= 3 ? 'd-red' : it.dday <= 7 ? 'd-amber' : 'd-blue'}`}>{it.dday < 0 ? `D+${-it.dday}` : it.dday === 0 ? 'D-DAY' : `D-${it.dday}`}</span>
          </Link>
        )) : <div className="empty">30일 이내 갱신·활성 예정인 키가 없습니다.</div>}
      </div>
    </>
  )
}

/* ---------- 최근 활동 (감사 로그 최신 10건, SSE 로 추가) ---------- */
const BAD_ACTION = /FAILED|VIOLATION|TAMPERED/
export function FeedCard({ items, fresh }: { items: AuditLogItem[]; fresh: Set<number> }) {
  return (
    <>
      <CardHead id="feed" title="최근 활동" more={<Link className="more" to="/audit">전체 보기 →</Link>} />
      <div className="body">
        {items.length ? items.map((a) => (
          <Link key={a.id} className={`feed-it ${fresh.has(a.id) ? 'new' : ''}`} to="/audit" state={{ detail: a }}>
            <span className={`who ${a.actor === 'SYSTEM' ? 'sys' : ''}`}>{a.actor}</span>
            <span className="what"><span className={`actchip ${BAD_ACTION.test(a.action) ? 'bad' : ''}`}>{a.action}</span><span className="mono">{a.target}</span></span>
            <span className="when" title={a.createdAt}>{relTime(a.createdAt)}</span>
          </Link>
        )) : <div className="empty">기록이 없습니다.</div>}
      </div>
    </>
  )
}

/* ---------- 보안 신호 (최근 24시간) ---------- */
export function SignalsCard({ signals }: { signals: Signal[] }) {
  return (
    <>
      <CardHead id="signals" title="보안 신호" right={<span className="hint">최근 24시간</span>} />
      <div className="body">
        {signals.map((s) => (
          <div key={s.key} className="sig-it"><div>{s.label}<small>{s.sub}</small></div><span className={`v ${s.value ? s.level : ''}`}>{s.value}</span></div>
        ))}
      </div>
    </>
  )
}

/* ---------- 연산 실패 (최근 30일) ---------- */
export function FailuresCard({ failures }: { failures: Failure[] }) {
  return (
    <>
      <CardHead id="fails" title={<>연산 실패 <span className={`cnt ${failures.length ? 'red' : ''}`}>{failures.length}</span></>} right={<span className="hint">최근 30일</span>} />
      <div className="body">
        {failures.length ? failures.map((f, i) => (
          <Link key={`${f.keyUid}-${f.usedAt}-${i}`} className="fail-it" to={`/keys/${f.keyUid}`}>
            <div className="kn"><b>{f.keyName} <span className="mono" style={{ color: 'var(--text-3)', fontWeight: 500 }}>v{f.version} · {f.operation}</span></b><span title={f.failReason ?? ''}>{f.failReason ?? '—'}</span></div>
            <span className="when" title={f.usedAt}>{relTime(f.usedAt)}</span>
          </Link>
        )) : <div className="empty">최근 30일 실패한 연산이 없습니다.</div>}
      </div>
    </>
  )
}

/* ---------- 알고리즘 분포 (폐기 제외) ---------- */
export function AlgoCard({ algorithms }: { algorithms: AlgoCount[] }) {
  const max = algorithms.length ? algorithms[0].count : 1
  return (
    <>
      <CardHead id="algos" title="알고리즘 분포" right={<span className="hint">폐기 제외</span>} />
      <div className="body bars">
        {algorithms.length ? algorithms.map((a) => (
          <div key={a.algorithm} className="bar-it"><span className="nm">{a.algorithm}</span><div className="tr"><div className="fl" style={{ width: `${(a.count / max) * 100}%` }} /></div><span className="v">{a.count}</span></div>
        )) : <div className="empty">등록된 키가 없습니다.</div>}
      </div>
    </>
  )
}
