/**
 * 클립보드 복사 — navigator.clipboard 는 보안 컨텍스트(HTTPS·localhost)에서만 존재한다.
 * 배포 서버(http://192.168.200.52)는 HTTP 라 undefined 이므로 execCommand('copy') 폴백을 둔다.
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
  const ta = document.createElement('textarea')
  ta.value = text
  ta.setAttribute('readonly', '')
  ta.style.cssText = 'position:fixed;top:0;left:0;width:1px;height:1px;opacity:0;pointer-events:none'
  document.body.appendChild(ta)
  ta.select()
  ta.setSelectionRange(0, text.length)
  let ok = false
  try {
    ok = document.execCommand('copy')
  } finally {
    document.body.removeChild(ta)
  }
  if (!ok) throw new Error('clipboard unavailable')
}
