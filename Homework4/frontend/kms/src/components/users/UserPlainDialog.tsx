import { useState } from 'react'
import { viewUserPlain, type UserPlain, type UserSummary } from '@/api/users'
import { Button } from '@/components/ui/button'
import { Dialog, DialogBody, DialogContent, DialogFooter } from '@/components/ui/dialog'
import { errorMessage, useToast } from '@/components/ui/toast'

/* 개인정보 원문 조회 — ADMIN 한정, 사유 필수, 조회 즉시 감사로그(USER_PLAIN_VIEWED) 기록 */
export function UserPlainDialog({ user, onClose }: { user: UserSummary; onClose: () => void }) {
  const toast = useToast()
  const [reason, setReason] = useState('')
  const [pending, setPending] = useState(false)
  const [result, setResult] = useState<UserPlain | null>(null)

  async function run() {
    if (!reason.trim()) { toast('조회 사유는 필수 입력입니다 (400 Bad Request)', 'error'); return }
    setPending(true)
    try {
      const { data, message } = await viewUserPlain(user.id, reason.trim())
      setResult(data)
      toast(message ?? '원문이 조회되었습니다.')
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
          {!result ? (
            <div className="field">
              <label>조회 사유 <em>*</em> <span className="badge b-bad" style={{ marginLeft: 6 }}>ADMIN 한정</span></label>
              <textarea className="input txt" style={{ minHeight: 70 }} value={reason} onChange={(e) => setReason(e.target.value)}
                placeholder="예: CS 본인확인 요청 처리" />
              <div className="help">조회 즉시 감사로그(USER_PLAIN_VIEWED)에 사유와 함께 기록됩니다</div>
            </div>
          ) : (
            <>
              <div className="meta-box"><div className="k">이름</div><div className="v">{result.name}</div></div>
              <div className="meta-box"><div className="k">연락처 (복호화됨)</div><div className="v mono">{result.phone}</div></div>
              <div className="meta-box"><div className="k">이메일 (복호화됨)</div><div className="v mono">{result.email}</div></div>
            </>
          )}
        </DialogBody>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>닫기</Button>
          {!result && <Button disabled={pending} onClick={run}>복호화 조회</Button>}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
