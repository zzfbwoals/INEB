import { useToast } from '@/components/ui/toast'
import { copyText } from '@/lib/clipboard'

/* 복사 버튼 — uid·공개키·암호문 등 원클릭 복사 (상용 KMS 콘솔 공통 관례) */

const COPY_ICON = (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <rect x="9" y="9" width="11" height="11" rx="2" />
    <path d="M5 15V5a2 2 0 0 1 2-2h10" />
  </svg>
)

export const DOWNLOAD_ICON = (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M12 3v12m0 0-4.5-4.5M12 15l4.5-4.5M4 20h16" />
  </svg>
)

export function CopyButton({ text, label = '복사', title }: { text: string; label?: string; title?: string }) {
  const toast = useToast()
  async function copy() {
    try {
      await copyText(text)
      toast('복사되었습니다')
    } catch {
      toast('복사에 실패했습니다', 'error')
    }
  }
  return <button type="button" className="copy-btn" title={title} onClick={copy}>{COPY_ICON}{label}</button>
}
