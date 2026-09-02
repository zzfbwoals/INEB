import { useState } from 'react'
import { updateKey, type KeyDetail } from '@/api/keys'
import { ROT_DEFAULT, ROT_MAX, ROT_MIN } from '@/lib/keyRules'
import { fromDateTimeLocal, toDateTimeLocal } from '@/lib/format'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogBody, DialogContent, DialogFooter } from '@/components/ui/dialog'
import { errorMessage, useToast } from '@/components/ui/toast'

/* 목업 "키 메타정보 수정" — 키명·설명·갱신 주기·(PRE_ACTIVE 현행) 활성일 */
export function KeyEditDialog({ detail, open, onClose, onDone }: { detail: KeyDetail; open: boolean; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const current = detail.versions.find((v) => v.version === detail.currentVersion)
  const [keyName, setKeyName] = useState(detail.keyName)
  const [description, setDescription] = useState(detail.description ?? '')
  const [autoRotate, setAutoRotate] = useState(detail.autoRotate)
  const [rotationDays, setRotationDays] = useState(detail.rotationPeriodDays ?? ROT_DEFAULT)
  const [activationDate, setActivationDate] = useState(toDateTimeLocal(current?.activationDate))
  const [pending, setPending] = useState(false)
  const editableDate = current?.state === 'PRE_ACTIVE'

  async function submit() {
    if (!keyName.trim()) { toast('키명을 입력해주세요', 'error'); return }
    if (autoRotate && (rotationDays < ROT_MIN || rotationDays > ROT_MAX)) { toast('갱신 주기는 1~730일이어야 합니다', 'error'); return }
    setPending(true)
    try {
      await updateKey(detail.keyUid, {
        keyName: keyName.trim(),
        description: description.trim() || null,
        autoRotate,
        rotationPeriodDays: autoRotate ? rotationDays : null,
        activationDate: editableDate && activationDate !== toDateTimeLocal(current?.activationDate) ? fromDateTimeLocal(activationDate) : null,
      })
      toast('키 정보가 수정되었습니다')
      onClose()
      onDone()
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent title="키 메타정보 수정">
        <DialogBody>
          <div className="field"><label>키명</label><Input value={keyName} onChange={(e) => setKeyName(e.target.value)} /></div>
          <div className="field"><label>설명</label><Input value={description} onChange={(e) => setDescription(e.target.value)} /></div>
          <div className="field">
            <label>갱신 주기 (회전)</label>
            <div className="rot-row">
              <label className="sw"><input type="checkbox" checked={autoRotate} onChange={(e) => setAutoRotate(e.target.checked)} />자동 갱신</label>
              <input className="input mono" type="number" min={ROT_MIN} max={ROT_MAX} value={rotationDays} disabled={!autoRotate} onChange={(e) => setRotationDays(Number(e.target.value))} />
              <span style={{ fontSize: 13, color: 'var(--text-2)' }}>일</span>
            </div>
          </div>
          {editableDate && current && (
            <div className="field">
              <label>활성일 (v{current.version})</label>
              <Input className="mono" type="datetime-local" value={activationDate} onChange={(e) => setActivationDate(e.target.value)} />
            </div>
          )}
        </DialogBody>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>취소</Button>
          <Button disabled={pending} onClick={submit}>저장</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
