import { useSyncExternalStore } from 'react'

/** 목업 theme.js 포팅 — 다크/라이트/시스템 3모드, localStorage 'kms.theme' */
export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'kms.theme'
const listeners = new Set<() => void>()

export function getThemeMode(): ThemeMode {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    if (value === 'light' || value === 'dark' || value === 'system') return value
  } catch {
    // localStorage 접근 불가 시 시스템 설정 사용
  }
  return 'system'
}

export function applyTheme(): void {
  const mode = getThemeMode()
  const dark =
    mode === 'dark' ||
    (mode === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
}

export function setThemeMode(mode: ThemeMode): void {
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch {
    // 저장 실패해도 현재 세션에는 적용
  }
  applyTheme()
  listeners.forEach((listener) => listener())
}

/** 앱 진입 시 1회 — 시스템 테마 변경을 따라가는 리스너 등록 */
export function initTheme(): void {
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (getThemeMode() === 'system') applyTheme()
  })
  applyTheme()
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function useThemeMode(): ThemeMode {
  return useSyncExternalStore(subscribe, getThemeMode)
}
