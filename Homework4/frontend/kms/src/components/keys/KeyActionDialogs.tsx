import { useState } from 'react'
import { runKeyAction, type KeyAction, type KeyDetail, type VersionInfo } from '@/api/keys'
import { BADGE, STATE_KO } from '@/lib/keyRules'
import { fromDateTimeLocal } from '@/lib/format'
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
    if (!reason.trim()) { toast('사유를 입력해주세요', 'error'); return }
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
      <DialogContent title="키 활성화">
        <DialogBody>
          <ReasonField value={reason} onChange={setReason} placeholder="예: 연동 시스템 배포 완료로 예정보다 앞당겨 활성" />
        </DialogBody>
        <DialogFooter>
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
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={`재활성화 (REACTIVATE v${version})`}>
        <DialogBody>
          <ReasonField value={reason} onChange={setReason} placeholder="예: DB 점검 중 활성일 컬럼 수동 수정으로 인한 오탐 확인, 재암호화 위해 복구" />
        </DialogBody>
        <DialogFooter>
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
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={version ? '버전 정지' : '키 정지'}>
        <DialogBody>
          <ReasonField value={reason} onChange={setReason} placeholder="예: 해당 버전 암호문 전량 재암호화 완료" />
        </DialogBody>
        <DialogFooter>
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
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title="키 갱신">
        <DialogBody>
          <div className="field">
            <label>새 버전 활성일</label>
            <Input className="mono" type="datetime-local" value={activationDate} onChange={(e) => setActivationDate(e.target.value)} />
          </div>
          <ReasonField value={reason} onChange={setReason} placeholder="예: 수동 갱신 — 연동 시스템 교체" />
        </DialogBody>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>취소</Button>
          <Button disabled={pending || full} onClick={() => run('ROTATE', reason, { activationDate: fromDateTimeLocal(activationDate) })}>갱신</Button>
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
    if (target === null && allDisabled) { toast('운영 중인 버전이 있어 삭제할 수 없습니다', 'error'); return }
    run('DESTROY', reason, { version: target })
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title="키 삭제">
        <DialogBody>
          <div className="field">
            <label>삭제 대상</label>
            <div className="opt-row">
              {candidates.map((v) => (
                <label key={v.version} className={`opt ${target === v.version ? 'sel' : ''}`}>
                  <input type="radio" name="des" checked={target === v.version} onChange={() => setTarget(v.version)} />
                  <span>
                    <b>v{v.version}만 삭제 <span className={`badge ${BADGE[v.state]}`} style={{ marginLeft: 4 }}>{STATE_KO[v.state]}</span></b>
                  </span>
                </label>
              ))}
              <label className={`opt ${allDisabled ? 'dis' : ''} ${target === null && !allDisabled ? 'sel' : ''}`}>
                <input type="radio" name="des" disabled={allDisabled} checked={target === null && !allDisabled} onChange={() => setTarget(null)} />
                <span>
                  <b>키 전체 삭제 (준비·정지 버전 {candidates.length}개)</b>
                  {allDisabled && <span>{`ACTIVE 버전(${actives.map((v) => 'v' + v.version).join(', ')})이 있어 불가 (409) — 먼저 키 정지`}</span>}
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
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>취소</Button>
          <Button variant="danger" disabled={pending} onClick={submit}>삭제 실행</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
