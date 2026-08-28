import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import { isAxiosError } from 'axios'
import type { ApiEnvelope } from '@/api/auth'

/* 목업 toast() 이식 — 하단 중앙 단일 토스트, 2.6초 후 사라짐 */
type ToastFn = (message: string, kind?: 'ok' | 'error') => void

const ToastContext = createContext<ToastFn>(() => {})

export function ToastProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ message: string; kind: 'ok' | 'error'; show: boolean }>({ message: '', kind: 'ok', show: false })
  const timer = useRef<number | null>(null)

  const toast = useCallback<ToastFn>((message, kind = 'ok') => {
    setState({ message, kind, show: true })
    if (timer.current) window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setState((s) => ({ ...s, show: false })), 2600)
  }, [])

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div className={`toast ${state.kind === 'error' ? 'error' : ''} ${state.show ? 'show' : ''}`} role="status" aria-live="polite">
        <span className="dot" />
        <span>{state.message}</span>
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastFn {
  return useContext(ToastContext)
}

/** 백엔드 ApiEnvelope.message 를 우선 노출 (로그인 페이지 패턴) */
export function errorMessage(err: unknown, fallback = '요청을 처리하지 못했습니다.'): string {
  if (isAxiosError(err) && err.response) {
    const body = err.response.data as ApiEnvelope<unknown> | undefined
    return body?.message ?? fallback
  }
  return '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.'
}
