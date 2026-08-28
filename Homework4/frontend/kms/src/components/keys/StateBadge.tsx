import type { KeyState } from '@/api/keys'
import { BADGE, STATE_KO } from '@/lib/keyRules'

/** 목업 stateBadge() — "ACTIVE · 운영" */
export function StateBadge({ state }: { state: KeyState }) {
  return (
    <span className={`badge ${BADGE[state]}`}>
      {state} · {STATE_KO[state]}
    </span>
  )
}

export function IntegrityBadge({ valid }: { valid: boolean }) {
  return valid ? <span className="badge b-ok">정상</span> : <span className="badge b-bad">위반</span>
}
