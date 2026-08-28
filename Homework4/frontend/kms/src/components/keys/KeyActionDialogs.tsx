import { useState } from 'react'
import { runKeyAction, type KeyAction, type KeyDetail, type VersionInfo } from '@/api/keys'
import { BADGE, STATE_KO } from '@/lib/keyRules'
import { fmt, fromDateTimeLocal, isFuture } from '@/lib/format'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogBody, DialogContent, DialogFooter } from '@/components/ui/dialog'
import { errorMessage, useToast } from '@/components/ui/toast'

/* 목업 key-detail.html 의 연산 모달 5종 — 상태는 연산의 결과로만 변한다 */

export type ActionDialogState =
  | { kind: 'ACTIVATE'; version: number }
  | { kind: 'REACTIVATE'; version: number }
  | { kind: 'DEACTIVATE'; version: number | null }
  | { kind: 'ROTATE' }
  | { kind: 'DESTROY'; version: number | null }
  | null

interface Props {
  detail: KeyDetail
  state: ActionDialogState
  onClose: () => void
  onDone: () => void
}

function useRunner(detail: KeyDetail, onClose: () => void, onDone: () => void) {
  const toast = useToast()
  const [pending, setPending] = useState(false)
  async function run(action: KeyAction, reason: string, extra: { activationDate?: string | null; version?: number | null } = {}) {
    if (!reason.trim()) { toast('사유는 필수 입력입니다 (400 Bad Request)', 'error'); return }
    setPending(true)
    try {
      const { message } = await runKeyAction(detail.keyUid, { action, reason: reason.trim(), ...extra })
      toast(message ?? '처리되었습니다.')
      onClose()
      onDone()
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setPending(false)
    }
  }
  return { run, pending }
}

function ReasonField({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  return (
    <div className="field">
      <label>사유 <em>*</em></label>
      <textarea className="input txt" style={{ minHeight: 70 }} value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
    </div>
  )
}

export function KeyActionDialogs({ detail, state, onClose, onDone }: Props) {
  if (!state) return null
  switch (state.kind) {
    case 'ACTIVATE': return <ActivateDialog detail={detail} version={state.version} onClose={onClose} onDone={onDone} />
    case 'REACTIVATE': return <ReactivateDialog detail={detail} version={state.version} onClose={onClose} onDone={onDone} />
    case 'DEACTIVATE': return <DeactivateDialog detail={detail} version={state.version} onClose={onClose} onDone={onDone} />
    case 'ROTATE': return <RotateDialog detail={detail} onClose={onClose} onDone={onDone} />
    case 'DESTROY': return <DestroyDialog detail={detail} version={state.version} onClose={onClose} onDone={onDone} />
  }
}

/* ---- 활성화 ---- */
function ActivateDialog({ detail, version, onClose, onDone }: { detail: KeyDetail; version: number; onClose: () => void; onDone: () => void }) {
  const { run, pending } = useRunner(detail, onClose, onDone)
  const [reason, setReason] = useState('')
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title="활성화 (ACTIVATE)">
        <DialogBody>
          <div className="note info">
            <div>v{version}를 지금 활성화합니다. activation_date가 현재 시각으로 설정되고 v{version}가 최신 버전(암호화·서명 담당)이 됩니다. 기존 ACTIVE 버전은 그대로 복호화·검증에 사용됩니다.</div>
          </div>
          <ReasonField value={reason} onChange={setReason} placeholder="예: 연동 시스템 배포 완료로 예정보다 앞당겨 활성" />
        </DialogBody>
        <DialogFooter note="상태는 연산의 결과로만 변경됩니다">
          <Button variant="ghost" onClick={onClose}>취소</Button>
          <Button disabled={pending} onClick={() => run('ACTIVATE', reason, { version })}>지금 활성화</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/* ---- 재활성화 (무결성 위반 정지 버전 전용) ---- */
function ReactivateDialog({ detail, version, onClose, onDone }: { detail: KeyDetail; version: number; onClose: () => void; onDone: () => void }) {
  const { run, pending } = useRunner(detail, onClose, onDone)
  const [reason, setReason] = useState('')
  const willCurrent = version > detail.currentVersion || detail.versions.every((v) => v.state !== 'ACTIVE')
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={`재활성화 (REACTIVATE v${version})`}>
        <DialogBody>
          <div className="note info">
            <div>v{version}는 무결성 위반으로 자동 정지된 버전입니다. 재활성화하면{' '}
            {willCurrent ? <><b>최신 버전으로 복귀</b>해 암호화·서명에도 사용됩니다</> : <><b>복호화·검증 전용</b> ACTIVE가 되어 재암호화 작업에 사용할 수 있습니다</>}.</div>
          </div>
          <div className="note">
            <div>서버 검증 순서: ① 마스터키로 언래핑 성공 확인(GCM 태그 — 키 재료 무손상) → ② 현재 메타로 integrity_hash 재계산·저장 → ③ ACTIVE 전이. 언래핑 실패 시 409(재료 손상 — 갱신만 가능).</div>
          </div>
          <ReasonField value={reason} onChange={setReason} placeholder="예: DB 점검 중 활성일 컬럼 수동 수정으로 인한 오탐 확인, 재암호화 위해 복구" />
        </DialogBody>
        <DialogFooter note="무결성 위반으로 정지된 버전만 가능 · ADMIN 한정 · 감사로그 기록">
          <Button variant="ghost" onClick={onClose}>취소</Button>
          <Button disabled={pending} onClick={() => run('REACTIVATE', reason, { version })}>재활성화</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/* ---- 정지 (버전 / 키 전체) ---- */
function DeactivateDialog({ detail, version, onClose, onDone }: { detail: KeyDetail; version: number | null; onClose: () => void; onDone: () => void }) {
  const { run, pending } = useRunner(detail, onClose, onDone)
  const [reason, setReason] = useState('')
  const actives = detail.versions.filter((v) => v.state === 'ACTIVE').map((v) => 'v' + v.version).join(', ')
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={version ? `버전 정지 (DEACTIVATE v${version})` : '키 정지 (DEACTIVATE)'}>
        <DialogBody>
          <div className="note warn">
            <div>{version
              ? <>v{version}를 정지합니다. 이후 이 버전으로는 <b>복호화·검증도 차단</b>되며 재활성화할 수 없습니다. 이 버전으로 암호화된 데이터가 남아 있지 않은지(재암호화 완료) 확인하세요.</>
              : <>키의 모든 ACTIVE 버전({actives})을 정지합니다. 이후 <b>암복호화·서명검증이 전부 차단</b>되고 자동 갱신도 중단됩니다. 재활성화는 불가하며 다시 쓰려면 갱신으로 새 버전을 생성해야 합니다.</>}</div>
          </div>
          <ReasonField value={reason} onChange={setReason} placeholder="예: 해당 버전 암호문 전량 재암호화 완료" />
        </DialogBody>
        <DialogFooter note="관리자 정지는 재활성화 불가(갱신으로 대체) · 무결성 자동 정지만 재활성화 가능">
          <Button variant="ghost" onClick={onClose}>취소</Button>
          <Button variant="danger" disabled={pending} onClick={() => run('DEACTIVATE', reason, { version })}>정지</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/* ---- 갱신 ---- */
function RotateDialog({ detail, onClose, onDone }: { detail: KeyDetail; onClose: () => void; onDone: () => void }) {
  const { run, pending } = useRunner(detail, onClose, onDone)
  const [reason, setReason] = useState('')
  const [activationDate, setActivationDate] = useState('')
  const full = detail.versionCount >= detail.maxVersions
  const scheduled = detail.versions.find((v) => v.state === 'PRE_ACTIVE')
  const next = detail.versions.reduce((m, v) => Math.max(m, v.version), 0) + 1
  const future = isFuture(activationDate)
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title="갱신 (ROTATE) — 새 버전 생성">
        <DialogBody>
          <div className="note">
            <div>{full ? <>버전 상한({detail.maxVersions})에 도달했습니다. 사용하지 않는 구 버전을 정지·삭제한 뒤 갱신하세요.</>
              : scheduled ? <>이미 v{scheduled.version}가 활성 예약 중입니다. 새로 갱신하면 v{scheduled.version}는 삭제되고 v{next}가 생성됩니다.</>
                : <>새 버전 <b>v{next}</b>을 생성합니다 ({detail.algorithm}-{detail.keySize}, SecureRandom 새 재료 · 마스터키 래핑). 기존 ACTIVE 버전은 그대로 유지되어 복호화·검증에 계속 사용됩니다.</>}</div>
          </div>
          <div className="field">
            <label>새 버전 활성일</label>
            <Input className="mono" type="datetime-local" value={activationDate} onChange={(e) => setActivationDate(e.target.value)} />
            <div className="help">
              {future ? <>미래 → v{next}는 <b>PRE_ACTIVE</b>로 예약, {activationDate.replace('T', ' ')} 도래 시 최신 버전으로 교체</>
                : <>비우면 즉시 → v{next} <b>ACTIVE</b>(최신, 암호화·서명 담당). v{detail.currentVersion}은 ACTIVE 유지(복호화·검증 전용)</>}
            </div>
          </div>
          <ReasonField value={reason} onChange={setReason} placeholder="예: 수동 갱신 — 연동 시스템 교체" />
        </DialogBody>
        <DialogFooter note="수동 갱신은 다음 자동 갱신(갱신 주기) 스케줄에 영향을 주지 않음">
          <Button variant="ghost" onClick={onClose}>취소</Button>
          <Button disabled={pending || full} onClick={() => run('ROTATE', reason, { activationDate: fromDateTimeLocal(activationDate) })}>갱신 실행</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/* ---- 삭제 (버전 / 키 전체) ---- */
function DestroyDialog({ detail, version, onClose, onDone }: { detail: KeyDetail; version: number | null; onClose: () => void; onDone: () => void }) {
  const { run, pending } = useRunner(detail, onClose, onDone)
  const toast = useToast()
  const [reason, setReason] = useState('')
  const [confirm, setConfirm] = useState('')
  const [target, setTarget] = useState<number | null>(version)
  const actives = detail.versions.filter((v) => v.state === 'ACTIVE')
  const candidates: VersionInfo[] = detail.versions.filter((v) => v.state === 'DEACTIVATED' || v.state === 'PRE_ACTIVE')
  const allDisabled = actives.length > 0

  function submit() {
    if (confirm.trim() !== detail.keyName) { toast('키명이 일치하지 않습니다', 'error'); return }
    if (target === null && allDisabled) { toast('운영 중인 버전이 있어 키 전체 삭제 불가 (409 Conflict)', 'error'); return }
    run('DESTROY', reason, { version: target })
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title="삭제 (DESTROY)">
        <DialogBody>
          <div className="note warn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ flex: 'none', marginTop: 2 }}><path d="M12 3 2.5 20h19L12 3z" /><path d="M12 10v4m0 3.2v.1" /></svg>
            <div>키 재료(wrapped_key)가 삭제되어 <b>복구할 수 없습니다.</b> 해당 버전으로 암호화된 데이터는 영구히 복호화할 수 없습니다. 메타·이력은 감사 목적으로 보존됩니다.</div>
          </div>
          <div className="field">
            <label>삭제 대상</label>
            <div className="opt-row">
              {candidates.map((v) => (
                <label key={v.version} className={`opt ${target === v.version ? 'sel' : ''}`}>
                  <input type="radio" name="des" checked={target === v.version} onChange={() => setTarget(v.version)} />
                  <span>
                    <b>v{v.version}만 삭제 <span className={`badge ${BADGE[v.state]}`} style={{ marginLeft: 4 }}>{STATE_KO[v.state]}</span></b>
                    <span>마지막 사용 {fmt(v.lastUsedAt)} · 누적 {v.usageCount.toLocaleString()}회</span>
                  </span>
                </label>
              ))}
              <label className={`opt ${allDisabled ? 'dis' : ''} ${target === null && !allDisabled ? 'sel' : ''}`}>
                <input type="radio" name="des" disabled={allDisabled} checked={target === null && !allDisabled} onChange={() => setTarget(null)} />
                <span>
                  <b>키 전체 삭제 (준비·정지 버전 {candidates.length}개)</b>
                  <span>{allDisabled ? `ACTIVE 버전(${actives.map((v) => 'v' + v.version).join(', ')})이 있어 불가 (409) — 먼저 키 정지` : '모든 버전 삭제 후 키 상태가 DESTROYED가 됩니다'}</span>
                </span>
              </label>
            </div>
          </div>
          <div className="field">
            <label>확인을 위해 키명 입력 <em>*</em></label>
            <Input className="mono" value={confirm} onChange={(e) => setConfirm(e.target.value)} placeholder="키명을 정확히 입력" />
          </div>
          <ReasonField value={reason} onChange={setReason} placeholder="예: 해당 버전 암호문 전량 파기 확인" />
        </DialogBody>
        <DialogFooter note="DESTROYED는 종단 상태">
          <Button variant="ghost" onClick={onClose}>취소</Button>
          <Button variant="danger" disabled={pending} onClick={submit}>삭제 실행</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
