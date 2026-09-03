import { useState } from 'react'
import { revealMaterial, type KeyDetail, type MaterialReveal } from '@/api/keys'
import { Button } from '@/components/ui/button'
import { Dialog, DialogBody, DialogContent, DialogFooter } from '@/components/ui/dialog'
import { errorMessage, useToast } from '@/components/ui/toast'

/* 버전 키값 조회 — 사유 필수, 조회 즉시 감사로그(KEY_MATERIAL_VIEWED) 기록 */
export function KeyRevealDialog({ detail, version, onClose }: { detail: KeyDetail; version: number; onClose: () => void }) {
  const toast = useToast()
  const [reason, setReason] = useState('')
  const [pending, setPending] = useState(false)
  const [result, setResult] = useState<MaterialReveal | null>(null)

  async function run() {
    if (!reason.trim()) { toast('사유를 입력해주세요', 'error'); return }
    setPending(true)
    try {
      const { data, message } = await revealMaterial(detail.keyUid, version, reason.trim())
      setResult(data)
      toast(message ?? '키 값이 조회되었습니다.')
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setPending(false)
    }
  }

  async function copy(text: string) {
    try { await navigator.clipboard.writeText(text); toast('복사되었습니다') } catch { toast('복사에 실패했습니다', 'error') }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={`키값 조회 (v${version})`}>
        <DialogBody>
          {!result ? (
            <div className="field">
              <label>조회 사유 <em>*</em></label>
              <textarea className="input txt" style={{ minHeight: 70 }} value={reason} onChange={(e) => setReason(e.target.value)}
                placeholder="예: 연동 시스템 키 배포를 위한 값 확인" />
            </div>
          ) : (
            <>
              <div className="field">
                <label>키 재료 ({result.algorithm}-{result.keySize}{result.publicKey ? ' · 개인키 PKCS#8' : ''} · Base64)</label>
                <div className="result-box ok" style={{ wordBreak: 'break-all', userSelect: 'none' }}>{result.material}</div>
              </div>
              {result.publicKey && (
                <div className="field">
                  <label>공개키 (X.509 · Base64)</label>
                  <div className="result-box" style={{ wordBreak: 'break-all', maxHeight: 120, overflowY: 'auto', userSelect: 'none' }}>{result.publicKey}</div>
                </div>
              )}
            </>
          )}
        </DialogBody>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>닫기</Button>
          {!result
            ? <Button disabled={pending} onClick={run}>조회</Button>
            : <Button onClick={() => copy(result.material)}>복사</Button>}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
