import { Copy, Download } from 'lucide-react'
import { useToast } from '@/components/ui/toast'
import { copyText } from '@/lib/clipboard'

/* 복사 버튼 — uid·공개키·암호문 등 원클릭 복사 (상용 KMS 콘솔 공통 관례) */

const COPY_ICON = <Copy size={12} />

export const DOWNLOAD_ICON = <Download size={12} />

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
