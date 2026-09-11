import type { AuditLogItem } from '@/api/audit'
import { Dialog, DialogBody, DialogContent } from '@/components/ui/dialog'

/* 감사 로그 한 건 상세 — 목록에서 정상 행을 클릭하면 열린다. 상세(detail)는 목록에서 말줄임되므로 여기서 전문을 보여준다 */
export function AuditLogDetailDialog({ item, onClose }: { item: AuditLogItem; onClose: () => void }) {
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={`감사 로그 #${item.id}`} wide>
        <DialogBody>
          <div className="meta-box">
            <div className="kv-grid">
              <div className="k">일시</div><div className="mono">{item.createdAt}</div>
              <div className="k">행위자</div><div><b>{item.actor}</b></div>
              <div className="k">행위</div><div><span className="actchip">{item.action}</span></div>
              <div className="k">대상</div><div className="mono">{item.target}</div>
              <div className="k">상세</div>
              <div className="mono" style={{ whiteSpace: 'pre-wrap' }}>
                {item.detail || '—'}
                {!item.detailDecrypted && <span style={{ color: 'var(--text-3)' }}> (암호화 이전 평문 행)</span>}
              </div>
            </div>
          </div>
        </DialogBody>
      </DialogContent>
    </Dialog>
  )
}
