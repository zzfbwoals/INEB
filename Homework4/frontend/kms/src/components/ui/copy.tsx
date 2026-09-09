import { Copy } from 'lucide-react'
import { useToast } from '@/components/ui/toast'
import { copyText } from '@/lib/clipboard'

/* 복사 버튼 — uid·공개키·암호문 등 원클릭 복사. 배경 없는 아이콘 + 툴팁(label, 기본 "복사") */

export function CopyButton({ text, label = '복사' }: { text: string; label?: string }) {
  const toast = useToast()
  async function copy() {
    try {
      await copyText(text)
      toast('복사되었습니다')
    } catch {
      toast('복사에 실패했습니다', 'error')
    }
  }
  return (
    <button type="button" className="icon-btn sm" data-tip={label} aria-label={label} onClick={copy}>
      <Copy size={13} />
    </button>
  )
}
