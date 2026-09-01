import { getToken } from './auth'

/* 실시간 이벤트 (SSE — GET /api/events). 모든 관리자 행위·시스템 이벤트는 감사 기록 시점에
   {action, target}으로 브로드캐스트되므로, 화면은 이걸 구독해 새로고침 없이 refetch 한다.
   EventSource 는 Authorization 헤더를 못 쓰므로 이 경로만 token 쿼리 파라미터를 쓴다. */

export interface UiEvent {
  action: string
  target: string
}

type Listener = (e: UiEvent) => void

let source: EventSource | null = null
const listeners = new Set<Listener>()

function ensureConnected() {
  if (source) return
  const token = getToken()
  if (!token) return
  const base = import.meta.env.VITE_API_BASE_URL ?? ''
  source = new EventSource(`${base}/api/events?token=${encodeURIComponent(token)}`)
  source.addEventListener('audit', (ev) => {
    try {
      const data = JSON.parse((ev as MessageEvent).data) as UiEvent
      listeners.forEach((l) => l(data))
    } catch {
      // 형식 오류 프레임은 무시
    }
  })
  // 연결 오류는 EventSource가 자동 재접속한다
}

/** 화면 useEffect 에서 호출 — 반환된 함수로 해제. 구독자가 없어지면 연결도 닫는다 */
export function subscribeUiEvents(listener: Listener): () => void {
  listeners.add(listener)
  ensureConnected()
  return () => {
    listeners.delete(listener)
    if (listeners.size === 0 && source) {
      source.close()
      source = null
    }
  }
}
