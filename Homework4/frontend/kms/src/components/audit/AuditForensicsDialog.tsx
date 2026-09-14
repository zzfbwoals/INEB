import { Check } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import type { AuditAck, AuditForensics, AuditLogItem } from '@/api/audit'
import { Dialog, DialogBody, DialogContent } from '@/components/ui/dialog'

/* 위반 상세 — 변조 증거(audit_violation 스냅샷)와 섀도(복사본)를 비교해 지워진·끼어든·바뀐 행을 원본|변조 값 diff 로 보여준다.
   증거는 처음 감지된 순간의 값이라 DB 를 원복한 뒤에도 그대로 남는다. 체인만 깨진 구간(섀도 차이 없음)은 "체인" 탭에 현재 값으로.
   all: 헤더 아이콘에서 열림, 탭으로 전체 목록 / single: 목록의 위반 행 클릭 — 그 행의 diff 만.
   확인(acknowledge, 2026-09-14): 제목 오른쪽(single) · 카드 머리(all)의 체크 — 회색은 미확인, 누르면 사유 입력 후 빨간 체크(확인됨).
   원복은 하지 않는다(append-only) — 확인은 "검토했다"는 기록이며, 전부 확인되면 제목 옆 색상점이 빨강에서 벗어난다.
   섀도·증거는 무결성 보장이 아니라 원본 증거(포렌식)이며, 탐지는 해시 체인이 담당한다. */

export type DiffKind = 'deleted' | 'inserted' | 'modified' | 'chain'
export type ForensicsView = { mode: 'all'; tab: DiffKind } | { mode: 'single'; kind: DiffKind; id: number }

export const FIELD_KO: Record<string, string> = {
  actor: '행위자', action: '행위', target: '대상', detail: '상세', prevHash: 'prev_hash', rowHash: 'row_hash', createdAt: '일시',
}
const BASE_FIELDS = ['actor', 'action', 'target', 'detail', 'createdAt', 'prevHash', 'rowHash']

function value(item: AuditLogItem | null, field: string): string {
  if (!item) return '—'
  switch (field) {
    case 'actor': return item.actor
    case 'action': return item.action
    case 'target': return item.target
    case 'detail': return item.detailDecrypted ? item.detail : `${item.detail} (복호화 실패 · 변조 추정)`
    case 'createdAt': return item.createdAt
    case 'prevHash': return item.prevHash ?? '—'
    case 'rowHash': return item.rowHash ?? '—'
    default: return '—'
  }
}

/** 카드 하나 = 행 하나의 diff. deleted 는 원본만, inserted 는 현재만 있고 그 자리는 "—". chain 은 현재 값만(구간 표시) */
type DiffEntry = { id: number; kind: DiffKind; original: AuditLogItem | null; current: AuditLogItem | null; fields: string[]; toId?: number }

function entries(data: AuditForensics): DiffEntry[] {
  return [
    ...data.deleted.map((r): DiffEntry => ({ id: r.id, kind: 'deleted', original: r, current: null, fields: BASE_FIELDS })),
    ...data.inserted.map((r): DiffEntry => ({ id: r.id, kind: 'inserted', original: null, current: r, fields: BASE_FIELDS })),
    ...data.modified.map((m): DiffEntry => ({ id: m.id, kind: 'modified', original: m.original, current: m.current, fields: m.fields })),
    ...(data.chain ?? []).map((c): DiffEntry => ({ id: c.id, kind: 'chain', original: null, current: c.current, fields: BASE_FIELDS, toId: c.toId })),
  ]
}

/** 감사 행(auditId)별 확인 상태 — 그 행에 걸린 증거가 전부 확인됐을 때만 확인됨(마지막 확인 기록) */
export function ackByAudit(data: AuditForensics | null): Map<number, AuditAck> {
  const out = new Map<number, AuditAck>()
  if (!data) return out
  const byAudit = new Map<number, (AuditAck | null)[]>()
  for (const e of data.evidence ?? []) {
    const list = byAudit.get(e.auditId) ?? []
    list.push(e.ack)
    byAudit.set(e.auditId, list)
  }
  for (const [auditId, acks] of byAudit) {
    if (acks.length > 0 && acks.every((a) => a !== null)) out.set(auditId, acks[acks.length - 1] as AuditAck)
  }
  return out
}

/** 툴팁은 짧게 — 사유까지 넣으면 모달·카드 폭을 넘어 잘린다 */
export function ackTip(a: AuditAck): string {
  return `확인됨 · ${a.by} · ${a.at}`
}

/** 확인 체크 버튼 — 미확인(회색)은 ADMIN 만 누를 수 있고, 확인됨은 빨간 체크 + 툴팁(누가·언제·사유).
    모달은 overflow:hidden 이라 아래 툴팁이 잘린다 — 제목 옆은 오른쪽(tip-right), 카드 머리(오른쪽 끝)는 왼쪽(tip-left) */
export function AckButton({ ack, canAck, onClick, className, tip: dir = 'right' }: {
  ack: AuditAck | null; canAck: boolean; onClick?: () => void; className?: string; tip?: 'right' | 'left'
}) {
  const tip = ack ? ackTip(ack) : canAck ? '위반 확인' : '미확인 (ADMIN 만 확인 가능)'
  return (
    <button type="button" className={`ack-btn tip-${dir}${ack ? ' on' : ''}${className ? ` ${className}` : ''}`} data-tip={tip} aria-label={tip}
      disabled={!!ack || !canAck} onClick={(e) => { e.stopPropagation(); if (!ack && canAck) onClick?.() }}>
      <Check size={16} strokeWidth={2.6} />
    </button>
  )
}

export function AuditForensicsDialog({ data, view, canAck, onAck, onClose }: {
  data: AuditForensics; view: ForensicsView; canAck: boolean; onAck: (auditId: number) => void; onClose: () => void
}) {
  const [tab, setTab] = useState<DiffKind>(view.mode === 'all' ? view.tab : view.kind)
  const all = entries(data)
  const acks = ackByAudit(data)
  const single = view.mode === 'single' ? all.find((e) => e.kind === view.kind && e.id === view.id) ?? null : null
  const noDiff = data.deletedCount + data.insertedCount + data.modifiedCount === 0
  const listed = view.mode === 'single' ? (single ? [single] : []) : all.filter((e) => e.kind === tab)
  const chainCount = data.chainCount ?? 0

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={view.mode === 'single' ? `감사 로그 위반 상세 #${view.id}` : '감사 로그 위반 상세'} wide className="scroll-body"
        titleRight={view.mode === 'single' && single ? <AckButton ack={acks.get(single.id) ?? null} canAck={canAck} onClick={() => onAck(single.id)} /> : undefined}
        headerExtra={view.mode === 'all' && (
          <div className="tabs">
            <button type="button" className={`tab${tab === 'deleted' ? ' on' : ''}`} onClick={() => setTab('deleted')}>삭제됨 {data.deletedCount}</button>
            <button type="button" className={`tab${tab === 'inserted' ? ' on' : ''}`} onClick={() => setTab('inserted')}>삽입됨 {data.insertedCount}</button>
            <button type="button" className={`tab${tab === 'modified' ? ' on' : ''}`} onClick={() => setTab('modified')}>수정됨 {data.modifiedCount}</button>
            {chainCount > 0 && <button type="button" className={`tab${tab === 'chain' ? ' on' : ''}`} onClick={() => setTab('chain')}>체인 {chainCount}</button>}
          </div>
        )}>
        <DialogBody>
          {!data.chainValid && noDiff && chainCount === 0 && (
            <div className="result-box bad">체인 위반이 있으나 섀도와 차이가 없습니다 — 원본 증거(섀도)도 함께 변조된 것으로 추정됩니다.</div>
          )}
          <div className="scroll-list">
            {listed.length === 0 && <div className="tbl-empty">{view.mode === 'single' ? '해당 행의 위반 정보가 없습니다' : '해당하는 행이 없습니다'}</div>}
            {listed.map((e) => (
              <DiffCard key={`${e.kind}-${e.id}`} entry={e} showHeader={view.mode === 'all'}
                ack={view.mode === 'all' ? <AckButton ack={acks.get(e.id) ?? null} canAck={canAck} onClick={() => onAck(e.id)} className="sm" tip="left" /> : null} />
            ))}
          </div>
        </DialogBody>
      </DialogContent>
    </Dialog>
  )
}

function DiffCard({ entry, showHeader, ack }: { entry: DiffEntry; showHeader: boolean; ack: ReactNode }) {
  const isChain = entry.kind === 'chain'
  const changed = new Set(entry.kind === 'modified' ? entry.fields : isChain ? [] : BASE_FIELDS)
  const shown = BASE_FIELDS
  return (
    <div className="meta-box">
      {(showHeader || isChain) && (
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
          <span className="mono" style={{ color: 'var(--text-3)' }}>#{entry.id}{isChain && entry.toId && entry.toId !== entry.id ? ` ~ #${entry.toId}` : ''}</span>
          {entry.kind === 'modified' && <span style={{ fontSize: 12, color: 'var(--text-3)' }}>{entry.fields.map((f) => FIELD_KO[f] ?? f).join(', ')}</span>}
          {isChain && <span style={{ fontSize: 12, color: 'var(--text-3)' }}>체인 끊김 — 복사본과 차이가 없어 원본 값을 알 수 없음</span>}
          <span style={{ marginLeft: 'auto' }}>{showHeader && ack}</span>
        </div>
      )}
      <div className="diff-grid">
        <div className="h">필드</div><div className="h">원본</div><div className="h">{isChain ? '현재 값' : '변조 값'}</div>
        {shown.map((f) => (
          <ContentsRow key={f} label={FIELD_KO[f] ?? f} original={value(entry.original, f)} current={value(entry.current, f)}
            changed={changed.has(f)} hasOriginal={!!entry.original} hasCurrent={!!entry.current} hash={f === 'prevHash' || f === 'rowHash'} />
        ))}
      </div>
    </div>
  )
}

/* git diff 색: 원본(빠진 값)은 빨강 "-", 현재(들어온 값)는 초록 "+". 해시(64자)는 열 너비만큼만 보이고 잘리는 자리에 … */
function ContentsRow({ label, original, current, changed, hasOriginal, hasCurrent, hash }: {
  label: string; original: string; current: string; changed: boolean; hasOriginal: boolean; hasCurrent: boolean; hash?: boolean
}) {
  const h = hash ? ' hash' : ''
  return (
    <>
      <div className="k">{label}</div>
      <div className={`mono${h}${changed && hasOriginal ? ' del' : ''}`}>{original}</div>
      <div className={`mono${h}${changed && hasCurrent ? ' add' : ''}`}>{current}</div>
    </>
  )
}
