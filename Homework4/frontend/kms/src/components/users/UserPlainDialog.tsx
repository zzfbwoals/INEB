import { useState } from 'react'
import { viewUserPlain, type UserPlain, type UserSummary } from '@/api/users'
import { Button } from '@/components/ui/button'
import { Dialog, DialogBody, DialogContent, DialogFooter } from '@/components/ui/dialog'
import { errorMessage, useToast } from '@/components/ui/toast'

/* 개인정보 원문 조회 사유 입력 — ADMIN 한정, 조회 즉시 감사로그(USER_PLAIN_VIEWED) 기록.
   원문은 모달이 아니라 목록 행의 마스킹을 풀어 보여준다(onRevealed) */
export function UserPlainDialog({ user, onClose, onRevealed }: {
  user: UserSummary
  onClose: () => void
  onRevealed: (plain: UserPlain) => void
}) {
  const toast = useToast()
  const [reason, setReason] = useState('')
  const [pending, setPending] = useState(false)

  async function run() {
    if (!reason.trim()) { toast('조회 사유를 입력해주세요', 'error'); return }
    setPending(true)
    try {
      const { data, message } = await viewUserPlain(user.id, reason.trim())
      toast(message ?? '원문이 조회되었습니다.')
      onRevealed(data)
      onClose()
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title="개인정보 원문 조회">
        <DialogBody>
          <div className="field">
            <label>조회 사유 <em>*</em> <span className="badge b-bad" style={{ marginLeft: 6 }}>ADMIN 한정</span></label>
            <textarea className="input txt" style={{ minHeight: 70 }} value={reason} onChange={(e) => setReason(e.target.value)}
              placeholder="예: CS 본인확인 요청 처리" />
          </div>
        </DialogBody>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>취소</Button>
          <Button disabled={pending} onClick={run}>조회</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
