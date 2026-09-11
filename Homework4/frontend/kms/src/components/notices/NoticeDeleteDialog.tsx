import { useState } from 'react'
import { useNavigate } from 'react-router'
import { deleteNotice, type NoticeDetail } from '@/api/notices'
import { Button } from '@/components/ui/button'
import { Dialog, DialogBody, DialogContent, DialogFooter } from '@/components/ui/dialog'
import { errorMessage, useToast } from '@/components/ui/toast'

/* 목업 notice-detail.html 의 삭제 confirm 을 모달로 — 첨부(암호문)도 함께 지워진다 */
export function NoticeDeleteDialog({ notice, open, onClose }: { notice: NoticeDetail; open: boolean; onClose: () => void }) {
  const navigate = useNavigate()
  const toast = useToast()
  const [pending, setPending] = useState(false)

  async function submit() {
    setPending(true)
    try {
      const { message } = await deleteNotice(notice.id)
      toast(message ?? '공지사항이 삭제되었습니다.')
      onClose()
      navigate('/notices', { replace: true })
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent title="공지사항 삭제">
        <DialogBody>
          <div className="meta-box"><div className="v">{notice.title}</div></div>
          <p style={{ fontSize: 13, color: 'var(--text-2)', lineHeight: 1.7 }}>
            이 공지사항을 삭제할까요?
            {notice.files.length > 0 && ` 암호화된 첨부파일 ${notice.files.length}개도 함께 정리됩니다.`}
          </p>
        </DialogBody>
        <DialogFooter>
          <Button variant="danger" disabled={pending} onClick={submit}>삭제</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
