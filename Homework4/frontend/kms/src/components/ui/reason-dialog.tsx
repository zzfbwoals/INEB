import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogBody, DialogContent, DialogFooter } from '@/components/ui/dialog'
import { errorMessage, useToast } from '@/components/ui/toast'

/* 사유 입력 확인 모달 — 감사 통제가 붙는 관리자 행위 공용(무결성 재해시·위반 확인). 사유는 필수, ADMIN 한정 배지.
   run 이 성공하면 서버 메시지(없으면 doneMessage)를 토스트로 띄우고 닫는다 */
export function ReasonDialog({ title, confirmLabel, placeholder, doneMessage, onClose, run }: {
  title: string
  confirmLabel: string
  placeholder: string
  doneMessage: string
  onClose: () => void
  run: (reason: string) => Promise<{ message: string | null }>
}) {
  const toast = useToast()
  const [reason, setReason] = useState('')
  const [pending, setPending] = useState(false)

  async function submit() {
    if (!reason.trim()) { toast('사유를 입력해주세요', 'error'); return }
    setPending(true)
    try {
      const { message } = await run(reason.trim())
      toast(message ?? doneMessage)
      onClose()
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={title}>
        <DialogBody>
          <div className="field">
            <label>사유 <em>*</em> <span className="badge b-bad" style={{ marginLeft: 6 }}>ADMIN 한정</span></label>
            <textarea className="input txt" style={{ minHeight: 70 }} value={reason} onChange={(e) => setReason(e.target.value)}
              placeholder={placeholder} />
          </div>
        </DialogBody>
        <DialogFooter>
          <Button variant="danger" disabled={pending} onClick={submit}>{confirmLabel}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
