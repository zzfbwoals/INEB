import { Check, ChevronDown, KeyRound, MessageSquare, Plus, RotateCcw, RotateCw, ShieldCheck, User } from 'lucide-react'
import { Link } from 'react-router'
import type { AuditVerifyResult } from '@/api/audit'
import { PANEL_GROUPS, PANEL_TITLES, type PanelId } from '@/lib/splitTree'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'

/* 체인 밴드 — 상태는 감사 로그 아이콘 색(정상 초록 · 위반 빨강 · 확인 불가 회색), 텍스트는 툴팁 */
export function ChainBand({ chain, verifying, onVerify }: {
  chain: AuditVerifyResult | null | 'unavailable'
  verifying: boolean
  onVerify: () => void
}) {
  const ok = chain !== null && chain !== 'unavailable' && chain.healthy
  const bad = chain !== null && chain !== 'unavailable' && !chain.healthy
  const tip = chain === 'unavailable' ? '체인 확인 불가' : chain === null ? '확인 중' : ok ? '체인 정상' : `체인 위반 ${chain.violations.length}건`
  const r = chain !== null && chain !== 'unavailable' ? chain : null
  return (
    <div className={`chain-band ${bad ? 'bad' : ''}`}>
      <span className={`chain-ic ${ok ? 'ok' : bad ? 'bad' : 'off'}`} data-tip={tip} aria-label={tip}><ShieldCheck size={16} strokeWidth={2.2} /></span>
      <span>마지막 검증 <b className="mono">{r?.verifiedAt ?? '—'}</b></span>
      <button type="button" className="icon-btn sm" data-tip="체인 재검증" aria-label="체인 재검증" disabled={verifying} onClick={onVerify}>
        <RotateCw size={14} strokeWidth={2.2} />
      </button>
      <span className="sep" />
      <Link className="more" to="/audit">감사 로그 →</Link>
    </div>
  )
}

/* + 등록 메뉴 — 키·사용자·공지 등록 모달을 화면 이동 없이 연다 */
export function NewMenu({ onKey, onUser, onNotice }: { onKey: () => void; onUser: () => void; onNotice: () => void }) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button className="px-[10px]" data-tip="등록" aria-label="등록"><Plus size={15} strokeWidth={2.2} /></Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onSelect={onKey}><KeyRound size={16} />키 등록</DropdownMenuItem>
        <DropdownMenuItem onSelect={onUser}><User size={16} />사용자 등록</DropdownMenuItem>
        <DropdownMenuItem onSelect={onNotice}><MessageSquare size={16} />공지사항 등록</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

/* 패널 편집 — 카드 표시/숨김 토글(메뉴는 닫히지 않음) + 초기화 */
export function PanelMenu({ visible, onToggle, onReset }: { visible: Set<PanelId>; onToggle: (id: PanelId) => void; onReset: () => void }) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm">패널 편집<ChevronDown size={13} strokeWidth={2.2} /></Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" style={{ minWidth: 190 }}>
        {PANEL_GROUPS.map(([g, ids]) => (
          <div key={g}>
            <DropdownMenuLabel>{g}</DropdownMenuLabel>
            {ids.map((id) => (
              <DropdownMenuItem key={id} className={visible.has(id) ? 'on' : ''} onSelect={(e) => { e.preventDefault(); onToggle(id) }}>
                <span className="ck"><Check size={14} strokeWidth={2.6} /></span>{PANEL_TITLES[id]}
              </DropdownMenuItem>
            ))}
          </div>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={onReset}><span className="ck dim"><RotateCcw size={14} /></span>초기화</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
