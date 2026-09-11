import { useState } from 'react'
import type { AuditForensics, AuditLogItem } from '@/api/audit'
import { Dialog, DialogBody, DialogContent } from '@/components/ui/dialog'

/* 위반 상세 — 변조 증거(audit_violation 스냅샷)와 섀도(복사본)를 비교해 지워진·끼어든·바뀐 행을 원본|변조 값 diff 로 보여준다.
   증거는 처음 감지된 순간의 값이라 DB 를 원복한 뒤에도 그대로 남는다(감사 로그 위반은 영구).
   all: 헤더 아이콘에서 열림, 탭으로 전체 목록 / single: 목록의 위반 행 클릭 — 그 행의 diff 만.
   섀도·증거는 무결성 보장이 아니라 원본 증거(포렌식)이며, 탐지는 해시 체인이 담당한다. */

export type DiffKind = 'deleted' | 'inserted' | 'modified'
export type ForensicsView = { mode: 'all'; tab: DiffKind } | { mode: 'single'; kind: DiffKind; id: number }

export const FIELD_KO: Record<string, string> = {
  actor: '행위자', action: '행위', target: '대상', detail: '상세', prevHash: 'prev_hash', rowHash: 'row_hash', createdAt: '일시',
}
const BASE_FIELDS = ['actor', 'action', 'target', 'detail', 'createdAt']

function value(item: AuditLogItem | null, field: string): string {
  if (!item) return '—'
  switch (field) {
    case 'actor': return item.actor
    case 'action': return item.action
    case 'target': return item.target
    case 'detail': return item.detailDecrypted ? item.detail : `${item.detail} (복호화 실패 · 변조 추정)`
    case 'createdAt': return item.createdAt
    default: return '(해시 — 목록에 미노출)'
  }
}

/** 카드 하나 = 행 하나의 diff. deleted 는 원본만, inserted 는 현재만 있고 그 자리는 "—" */
type DiffEntry = { id: number; kind: DiffKind; original: AuditLogItem | null; current: AuditLogItem | null; fields: string[] }

function entries(data: AuditForensics): DiffEntry[] {
  return [
    ...data.deleted.map((r): DiffEntry => ({ id: r.id, kind: 'deleted', original: r, current: null, fields: BASE_FIELDS })),
    ...data.inserted.map((r): DiffEntry => ({ id: r.id, kind: 'inserted', original: null, current: r, fields: BASE_FIELDS })),
    ...data.modified.map((m): DiffEntry => ({ id: m.id, kind: 'modified', original: m.original, current: m.current, fields: m.fields })),
  ]
}

export function AuditForensicsDialog({ data, view, onClose }: { data: AuditForensics; view: ForensicsView; onClose: () => void }) {
  const [tab, setTab] = useState<DiffKind>(view.mode === 'all' ? view.tab : view.kind)
  const all = entries(data)
  const single = view.mode === 'single' ? all.find((e) => e.kind === view.kind && e.id === view.id) ?? null : null
  const noDiff = data.deletedCount + data.insertedCount + data.modifiedCount === 0
  const listed = view.mode === 'single' ? (single ? [single] : []) : all.filter((e) => e.kind === tab)

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={view.mode === 'single' ? `감사 로그 위반 상세 #${view.id}` : '감사 로그 위반 상세'} wide className="scroll-body"
        headerExtra={view.mode === 'all' && (
          <div className="tabs">
            <button type="button" className={`tab${tab === 'deleted' ? ' on' : ''}`} onClick={() => setTab('deleted')}>삭제됨 {data.deletedCount}</button>
            <button type="button" className={`tab${tab === 'inserted' ? ' on' : ''}`} onClick={() => setTab('inserted')}>삽입됨 {data.insertedCount}</button>
            <button type="button" className={`tab${tab === 'modified' ? ' on' : ''}`} onClick={() => setTab('modified')}>수정됨 {data.modifiedCount}</button>
          </div>
        )}>
        <DialogBody>
          {!data.chainValid && noDiff && (
            <div className="result-box bad">체인 위반이 있으나 섀도와 차이가 없습니다 — 원본 증거(섀도)도 함께 변조된 것으로 추정됩니다.</div>
          )}
          {data.guard !== 'ACTIVE' && (
            <div className="result-box bad">섀도 보호 트리거가 {data.guard === 'DISABLED' ? '해제' : '제거'}되어 있습니다. 재기동 시 자동 복구되며 AUDIT_SHADOW_GUARD_TAMPERED 로 기록됩니다.</div>
          )}
          <div className="scroll-list">
            {listed.length === 0 && <div className="tbl-empty">{view.mode === 'single' ? '해당 행의 위반 정보가 없습니다' : '해당하는 행이 없습니다'}</div>}
            {listed.map((e) => <DiffCard key={`${e.kind}-${e.id}`} entry={e} showHeader={view.mode === 'all'} />)}
          </div>
        </DialogBody>
      </DialogContent>
    </Dialog>
  )
}

function DiffCard({ entry, showHeader }: { entry: DiffEntry; showHeader: boolean }) {
  const changed = new Set(entry.kind === 'modified' ? entry.fields : BASE_FIELDS)
  const shown = entry.kind === 'modified'
    ? [...BASE_FIELDS, ...entry.fields.filter((f) => f === 'prevHash' || f === 'rowHash')]
    : BASE_FIELDS
  return (
    <div className="meta-box">
      {showHeader && (
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
          <span className="mono" style={{ color: 'var(--text-3)' }}>#{entry.id}</span>
          {entry.kind === 'modified' && <span style={{ fontSize: 12, color: 'var(--text-3)' }}>{entry.fields.map((f) => FIELD_KO[f] ?? f).join(', ')}</span>}
        </div>
      )}
      <div className="diff-grid">
        <div className="h">필드</div><div className="h">원본</div><div className="h">변조 값</div>
        {shown.map((f) => (
          <ContentsRow key={f} label={FIELD_KO[f] ?? f} original={value(entry.original, f)} current={value(entry.current, f)}
            changed={changed.has(f)} hasOriginal={!!entry.original} hasCurrent={!!entry.current} />
        ))}
      </div>
    </div>
  )
}

/* git diff 색: 원본(빠진 값)은 빨강 "-", 현재(들어온 값)는 초록 "+" */
function ContentsRow({ label, original, current, changed, hasOriginal, hasCurrent }: {
  label: string; original: string; current: string; changed: boolean; hasOriginal: boolean; hasCurrent: boolean
}) {
  return (
    <>
      <div className="k">{label}</div>
      <div className={`mono${changed && hasOriginal ? ' del' : ''}`}>{original}</div>
      <div className={`mono${changed && hasCurrent ? ' add' : ''}`}>{current}</div>
    </>
  )
}
