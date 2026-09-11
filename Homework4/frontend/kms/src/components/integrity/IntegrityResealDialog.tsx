import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogBody, DialogContent, DialogFooter } from '@/components/ui/dialog'
import { errorMessage, useToast } from '@/components/ui/toast'

/* 무결성 재해시 확인 모달 — 키·사용자 공용. 위반에서 정상으로 가는 유일한 경로라 한 번 더 확인하고 사유를 받는다.
   ADMIN 한정, 서버가 현재 값으로 해시를 다시 봉인하고 *_INTEGRITY_RESEALED 감사 기록을 남긴다 */
export function IntegrityResealDialog({ subject, onClose, run }: {
  /** 대상 표시명 — 예: '키 PAY-GW', '사용자 홍길동' */
  subject: string
  onClose: () => void
  run: (reason: string) => Promise<{ message: string | null }>
}) {
  const toast = useToast()
  const [reason, setReason] = useState('')
  const [pending, setPending] = useState(false)

  async function submit() {
    if (!reason.trim()) { toast('재해시 사유를 입력해주세요', 'error'); return }
    setPending(true)
    try {
      const { message } = await run(reason.trim())
      toast(message ?? '현재 값으로 재해시되었습니다.')
      onClose()
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title="현재 값으로 재해시">
        <DialogBody>
          <p className="reseal-note"><b>{subject}</b>의 무결성 해시를 지금 저장된 값으로 다시 계산하고 위반 표시를 해제합니다.</p>
          <div className="field">
            <label>사유 <em>*</em> <span className="badge b-bad" style={{ marginLeft: 6 }}>ADMIN 한정</span></label>
            <textarea className="input txt" style={{ minHeight: 70 }} value={reason} onChange={(e) => setReason(e.target.value)}
              placeholder="예: 변조 원인 확인 후 재봉인" />
          </div>
        </DialogBody>
        <DialogFooter>
          <Button variant="danger" disabled={pending} onClick={submit}>재해시</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
