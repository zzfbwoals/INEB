import { useCallback, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react'

/* 목록 테이블 열 너비 조절 — th 우측 가장자리를 드래그하면 그 열과 오른쪽 열의 폭을 맞바꾼다(합계 100% 유지,
   table-layout: fixed 라 가로 스크롤이 생기지 않는다). 더블클릭하면 기본값으로 복원. 화면별 key 로 localStorage 에 보존. */

const MIN_PCT = 4

function storageKey(key: string) {
  return `kms.cols.${key}`
}

function load(key: string, count: number): number[] | null {
  try {
    const raw = localStorage.getItem(storageKey(key))
    if (!raw) return null
    const arr: unknown = JSON.parse(raw)
    if (Array.isArray(arr) && arr.length === count && arr.every((x) => typeof x === 'number' && x > 0)) return arr as number[]
  } catch {
    /* 저장소 접근 불가 — 기본값 사용 */
  }
  return null
}

export function useColumnResize(key: string, defaults: number[]) {
  const [widths, setWidths] = useState<number[]>(() => load(key, defaults.length) ?? defaults)
  const tableRef = useRef<HTMLTableElement>(null)

  const startResize = useCallback((i: number, e: ReactMouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    const table = tableRef.current
    if (!table) return
    const handle = e.currentTarget
    handle.classList.add('active')
    const total = table.getBoundingClientRect().width || 1
    const startX = e.clientX
    const start = widths
    const pair = start[i] + start[i + 1]
    let latest = start
    const move = (ev: MouseEvent) => {
      const delta = ((ev.clientX - startX) / total) * 100
      const a = Math.min(Math.max(start[i] + delta, MIN_PCT), pair - MIN_PCT)
      const next = [...start]
      next[i] = a
      next[i + 1] = pair - a
      latest = next
      setWidths(next)
    }
    const up = () => {
      window.removeEventListener('mousemove', move)
      window.removeEventListener('mouseup', up)
      document.body.classList.remove('col-resizing')
      handle.classList.remove('active')
      try {
        localStorage.setItem(storageKey(key), JSON.stringify(latest))
      } catch {
        /* 저장 실패는 무시 — 이번 세션에만 적용 */
      }
    }
    document.body.classList.add('col-resizing')
    window.addEventListener('mousemove', move)
    window.addEventListener('mouseup', up)
  }, [widths, key])

  const reset = useCallback(() => {
    setWidths(defaults)
    try {
      localStorage.removeItem(storageKey(key))
    } catch {
      /* 무시 */
    }
  }, [defaults, key])

  /** th 안에 넣는 드래그 핸들 — 마지막 열은 오른쪽 짝이 없어 핸들이 없다 */
  function resizer(i: number) {
    if (i >= widths.length - 1) return null
    return (
      <span className="col-resizer" aria-hidden="true"
        onMouseDown={(e) => startResize(i, e)}
        onClick={(e) => e.stopPropagation()}
        onDoubleClick={(e) => { e.stopPropagation(); reset() }} />
    )
  }

  return { tableRef, widths, resizer }
}
