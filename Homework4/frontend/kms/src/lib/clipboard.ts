/**
 * 클립보드 복사 — navigator.clipboard 는 보안 컨텍스트(HTTPS·localhost)에서만 존재한다.
 * 배포 서버(http://192.168.200.52)는 HTTP 라 undefined 이므로 execCommand('copy') 폴백을 둔다.
 *
 * 폴백 주의: Radix Dialog 는 포커스 트랩이 있어 body 에 붙인 textarea 는 포커스를 받지 못한다(즉시 모달 안으로 되돌아감).
 * 선택이 비어도 execCommand 는 true 를 돌려주므로 "성공 토스트, 빈 클립보드"가 된다.
 * → 열린 다이얼로그 안에 textarea 를 붙이고, 실제 선택 여부를 확인한 뒤에만 성공으로 본다.
 */
export async function copyText(text: string): Promise<void> {
  if (window.isSecureContext && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch {
      /* 권한 거부 등 — 아래 폴백으로 진행 */
    }
  }
  const prev = document.activeElement as HTMLElement | null
  const host = prev?.closest<HTMLElement>('[role="dialog"]') ?? document.body
  const ta = document.createElement('textarea')
  ta.value = text
  ta.setAttribute('readonly', '')
  ta.setAttribute('aria-hidden', 'true')
  ta.style.cssText = 'position:fixed;top:0;left:0;width:1px;height:1px;opacity:0;pointer-events:none'
  host.appendChild(ta)
  let ok = false
  try {
    ta.focus({ preventScroll: true })
    ta.select()
    ta.setSelectionRange(0, text.length)
    const selected = document.activeElement === ta && ta.selectionEnd - ta.selectionStart === text.length
    ok = selected && document.execCommand('copy')
  } finally {
    host.removeChild(ta)
    prev?.focus?.({ preventScroll: true })
  }
  if (!ok) throw new Error('clipboard unavailable')
}
