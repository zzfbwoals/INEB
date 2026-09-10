import type { KeyState } from '@/api/keys'
import { STATE_KO } from '@/lib/keyRules'

/* 상태 표시는 배지 대신 색상점(2026-09-10) — 운영 초록 · 준비 파랑 · 정지 회색 · 폐기 회색 테두리. 이름 뒤에 붙이고 툴팁으로 상태명 */
export function StateDot({ state }: { state: KeyState }) {
  const label = `${state} · ${STATE_KO[state]}`
  return <span className={`sdot s-${state}`} data-tip={label} aria-label={label} />
}

/** 표 셀용 — 점 + 상태명 */
export function StateText({ state }: { state: KeyState }) {
  return <span className="stext"><span className={`sdot s-${state}`} />{STATE_KO[state]}</span>
}

export function UserDot({ status }: { status: string }) {
  const label = status === 'ACTIVE' ? '활성' : '정지'
  return <span className={`sdot u-${status === 'ACTIVE' ? 'ACTIVE' : 'SUSPENDED'}`} data-tip={label} aria-label={label} />
}

export function UserText({ status }: { status: string }) {
  return <span className="stext"><span className={`sdot u-${status === 'ACTIVE' ? 'ACTIVE' : 'SUSPENDED'}`} />{status === 'ACTIVE' ? '활성' : '정지'}</span>
}

export function IntegrityBadge({ valid }: { valid: boolean }) {
  return valid ? <span className="badge b-ok">정상</span> : <span className="badge b-bad">위반</span>
}
