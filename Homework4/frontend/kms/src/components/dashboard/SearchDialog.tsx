import * as DialogPrimitive from '@radix-ui/react-dialog'
import { CircleX, CornerDownLeft, KeyRound, MessageSquare, Pin, Search, ShieldCheck, User } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { search, type SearchResponse, type SearchType } from '@/api/search'
import type { KeySummary } from '@/api/keys'
import type { UserSummary } from '@/api/users'
import type { NoticeSummary } from '@/api/notices'
import type { AuditLogItem } from '@/api/audit'
import { PURPOSE_KO } from '@/lib/keyRules'
import { StateDot, UserDot } from '@/components/keys/StateBadge'
import type { KeyState } from '@/api/keys'
import { relTime } from '@/lib/format'

/* 통합 검색 모달 (토스증권 검색 참고) — "/" 또는 헤더 검색창으로 연다.
   탭(전체·키·사용자·공지사항·감사 로그)은 항상 표시, 검색어가 비면 목록·상세 모두 비움.
   전체 탭은 항목별 5건 + 구분선과 같은 줄의 "더 보기"(→ 해당 탭). 오른쪽은 선택 항목 상세.
   ↑↓ 탐색(마우스 올려도 선택) · ↵ 상세 화면 이동 · ESC 지우기→닫기. 입력은 300ms 디바운스 후 서버 검색 */

type Cat = 'keys' | 'users' | 'notices' | 'audits'
const TABS: [SearchType, string][] = [['ALL', '전체'], ['KEY', '키'], ['USER', '사용자'], ['NOTICE', '공지사항'], ['AUDIT', '감사 로그']]
const CATS: [Cat, string, SearchType][] = [['keys', '키', 'KEY'], ['users', '사용자', 'USER'], ['notices', '공지사항', 'NOTICE'], ['audits', '감사 로그', 'AUDIT']]

interface Row { cat: Cat; key: string; go: () => void; node: ReactNode; side: ReactNode }

function hl(s: string | null | undefined, q: string): ReactNode {
  const t = s ?? ''
  const i = q ? t.toLowerCase().indexOf(q.toLowerCase()) : -1
  if (i < 0) return t
  return <>{t.slice(0, i)}<mark>{t.slice(i, i + q.length)}</mark>{t.slice(i + q.length)}</>
}


export function SearchDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const nav = useNavigate()
  const inputRef = useRef<HTMLInputElement>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const [q, setQ] = useState('')
  const [tab, setTab] = useState<SearchType>('ALL')
  const [res, setRes] = useState<SearchResponse | null>(null)
  const [sel, setSel] = useState(0)

  // 열릴 때 초기화 + 포커스
  useEffect(() => {
    if (open) { setQ(''); setTab('ALL'); setRes(null); setSel(0); setTimeout(() => inputRef.current?.focus(), 30) }
  }, [open])

  // 디바운스 검색
  useEffect(() => {
    const kw = q.trim()
    if (!kw) { setRes(null); return }
    let cancelled = false
    const t = setTimeout(() => {
      search(kw, tab).then((r) => { if (!cancelled) { setRes(r); setSel(0) } }).catch(() => { if (!cancelled) setRes(null) })
    }, 300)
    return () => { cancelled = true; clearTimeout(t) }
  }, [q, tab])

  const kw = q.trim()
  const go = (path: string, state?: unknown) => { onOpenChange(false); nav(path, state ? { state } : undefined) }

  const rowsOf = (cat: Cat): Row[] => {
    if (!res) return []
    if (cat === 'keys') return res.keys.map((k: KeySummary) => ({
      cat, key: `k${k.keyUid}`, go: () => go(`/keys/${k.keyUid}`),
      node: <><span className="ic"><KeyRound size={14} /></span><div className="tx"><b>{hl(k.keyName, kw)}</b><span>{hl(k.algorithm, kw)}-{k.keySize}{k.mode ? ` · ${k.mode}` : ''} · v{k.currentVersion}</span></div><span className="rt"><StateDot state={k.status as KeyState} /></span></>,
      side: <><div className="sq-side-h">{k.keyName}</div><div className="mono" style={{ fontSize: 11, color: 'var(--text-3)' }}>{k.keyUid}</div>
        <dl><dt>알고리즘</dt><dd>{k.algorithm}-{k.keySize}{k.mode ? ` · ${k.mode}` : ''}</dd><dt>용도</dt><dd>{PURPOSE_KO[k.purpose as keyof typeof PURPOSE_KO] ?? k.purpose}</dd><dt>현재 버전</dt><dd className="mono">v{k.currentVersion} (총 {k.versionCount})</dd>
          <dt>자동 갱신</dt><dd>{k.autoRotate ? `${k.rotationPeriodDays}일 · 다음 ${k.nextRotationAt?.slice(0, 10) ?? '—'}` : '사용 안 함'}</dd><dt>무결성</dt><dd>{k.integrityValid ? '정상' : <span style={{ color: 'var(--red)' }}>위반</span>}</dd></dl></>,
    }))
    if (cat === 'users') return res.users.map((u: UserSummary) => ({
      cat, key: `u${u.id}`, go: () => go('/users', { editUser: u }),
      node: <><span className="ic"><User size={14} /></span><div className="tx"><b>{hl(u.name, kw)}</b><span className="mono">{u.phoneMasked} · {u.emailMasked}</span></div><span className="rt"><UserDot status={u.status} /></span></>,
      side: <><div className="sq-side-h">{u.name}</div>
        <dl><dt>연락처</dt><dd className="mono">{u.phoneMasked}</dd><dt>이메일</dt><dd className="mono">{u.emailMasked}</dd><dt>가입일</dt><dd className="mono">{u.createdAt}</dd><dt>무결성</dt><dd>{u.integrityValid ? '정상' : <span style={{ color: 'var(--red)' }}>위반</span>}</dd></dl>
        <div style={{ marginTop: 14, fontSize: 11.5, color: 'var(--text-3)' }}>원문은 사용자 관리에서 사유 입력 후 조회</div></>,
    }))
    if (cat === 'notices') return res.notices.map((n: NoticeSummary) => ({
      cat, key: `n${n.id}`, go: () => go(`/notices/${n.id}`),
      node: <><span className="ic"><MessageSquare size={14} /></span><div className="tx"><b>{n.pinned && <span className="pin-mark" style={{ marginRight: 5, verticalAlign: -2 }}><Pin size={12} /></span>}{hl(n.title, kw)}</b><span>{hl(n.authorName, kw)} · {n.createdAt.slice(0, 10)} · 조회 {n.viewCount}</span></div><span className="rt">{n.fileCount ? `📎 ${n.fileCount}` : ''}</span></>,
      side: <><div className="sq-side-h">{n.title}</div>{n.pinned && <span className="badge b-bad">고정</span>}
        <dl><dt>작성자</dt><dd>{n.authorName}</dd><dt>등록일</dt><dd className="mono">{n.createdAt}</dd><dt>조회수</dt><dd className="mono">{n.viewCount}</dd><dt>첨부</dt><dd>{n.fileCount ? `${n.fileCount}건 (암호화 저장)` : '없음'}</dd></dl></>,
    }))
    return res.audits.map((a: AuditLogItem) => ({
      cat, key: `a${a.id}`, go: () => go('/audit', { detail: a }),
      node: <><span className="ic"><ShieldCheck size={14} /></span><div className="tx"><b>{hl(a.action, kw)}</b><span>{hl(a.actor, kw)} · {hl(a.target, kw)}</span></div><span className="rt">{relTime(a.createdAt)}</span></>,
      side: <><div className="sq-side-h"><span className="actchip">{a.action}</span></div>
        <dl><dt>번호</dt><dd className="mono">#{a.id}</dd><dt>시각</dt><dd className="mono">{a.createdAt}</dd><dt>행위자</dt><dd>{a.actor}</dd><dt>대상</dt><dd className="mono">{a.target}</dd><dt>상세</dt><dd>{a.detail}</dd></dl></>,
    }))
  }

  const cats = tab === 'ALL' ? CATS : CATS.filter((c) => c[2] === tab)
  const sections = kw && res ? cats.map(([cat, label, type]) => ({ cat, label, type, rows: rowsOf(cat), count: res.counts[cat] })).filter((s) => s.rows.length) : []
  const rows = sections.flatMap((s) => s.rows)
  const cur = rows[sel]

  // 선택 행이 보이도록 목록만 스크롤 (본문 전체가 밀리지 않게)
  useEffect(() => {
    const list = listRef.current
    const el = list?.querySelector<HTMLElement>('.sq-row.on')
    if (!list || !el) return
    const lr = list.getBoundingClientRect(), rr = el.getBoundingClientRect()
    if (rr.top < lr.top + 8) list.scrollTop += rr.top - lr.top - 8
    else if (rr.bottom > lr.bottom - 8) list.scrollTop += rr.bottom - lr.bottom + 8
  }, [sel, rows.length])

  function onKey(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') { e.preventDefault(); if (rows.length) setSel((s) => (s + 1) % rows.length) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); if (rows.length) setSel((s) => (s - 1 + rows.length) % rows.length) }
    else if (e.key === 'Enter') { e.preventDefault(); cur?.go() }
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); if (q) { setQ(''); setTab('ALL') } else onOpenChange(false) }
  }

  let idx = -1
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="modal-bk" />
        <DialogPrimitive.Content className="modal srch" aria-describedby={undefined} onOpenAutoFocus={(e) => e.preventDefault()}>
          <DialogPrimitive.Title className="sr-only">통합 검색</DialogPrimitive.Title>
          <div className="srch-in">
            <Search size={17} strokeWidth={2.2} />
            <input ref={inputRef} value={q} onChange={(e) => setQ(e.target.value)} onKeyDown={onKey} placeholder="검색어를 입력해주세요" autoComplete="off" spellCheck={false} />
            <button type="button" className={`sq-clear ${q ? 'show' : ''}`} data-tip="지우기" aria-label="지우기" onClick={() => { setQ(''); setTab('ALL'); inputRef.current?.focus() }}><CircleX size={16} /></button>
          </div>
          <div className="tabs srch-tabs">
            {TABS.map(([t, l]) => <button key={t} type="button" className={`tab ${tab === t ? 'on' : ''}`} onClick={() => { setTab(t); inputRef.current?.focus() }}>{l}</button>)}
          </div>
          <div className="srch-body">
            <div className="srch-list" ref={listRef}>
              {sections.map((s) => (
                <div key={s.cat}>
                  <div className="sq-sec">{s.label}</div>
                  {s.rows.map((r) => { idx += 1; const i = idx; return (
                    <a key={r.key} className={`sq-row ${sel === i ? 'on' : ''}`} href="#" onMouseEnter={() => setSel(i)} onClick={(e) => { e.preventDefault(); r.go() }}>
                      {r.node}<span className="ent"><CornerDownLeft size={12} strokeWidth={2.2} /></span>
                    </a>
                  ) })}
                  {tab === 'ALL' && <div className="sq-more"><i />{s.count > 5 && <button type="button" onClick={() => { setTab(s.type); inputRef.current?.focus() }}>{s.label} 더 보기 ({s.count}) →</button>}</div>}
                </div>
              ))}
              {kw && res && !rows.length && <div className="sq-empty">'{kw}' 검색 결과가 없습니다</div>}
            </div>
            <div className="srch-side">{cur?.side}</div>
          </div>
          <div className="srch-foot">
            <span><kbd>↵</kbd>이동하기</span><span><kbd>ESC</kbd>지우기 · 닫기</span><span><kbd>↑</kbd><kbd>↓</kbd>탐색하기</span>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
