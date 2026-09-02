import { useLayoutEffect, useState, type RefObject } from 'react'

/* 목록 자동 페이징 — 행이 화면(뷰포트)을 벗어나지 않도록 남은 높이로 페이지 크기를 계산한다.
   행 높이는 실제 렌더된 행에서 측정(빈 목록이면 폴백값), 창 크기 변경 시 재계산 (반응형). */

const ROW_FALLBACK = 47 // tbody td 상하 padding 12*2 + 한 줄 내용 + 경계선
const PAGER_H = 52 // .pager 높이
const BOTTOM_GAP = 60 // .content 하단 여백

/** tbl-wrap 요소 ref 를 받아 페이지 크기를 돌려준다. 0 이면 아직 미계산 — fetch 를 보류할 것 */
export function useAutoPageSize(wrapRef: RefObject<HTMLDivElement | null>, min = 3): number {
  const [size, setSize] = useState(0)

  // 의존성 없이 렌더마다 재계산 — 데이터가 채워진 뒤 실측 행 높이로 한 번 보정되고, 값이 같으면 리렌더 없음
  useLayoutEffect(() => {
    function calc() {
      const wrap = wrapRef.current
      if (!wrap) return
      const body = wrap.querySelector('tbody')
      const top = (body ?? wrap).getBoundingClientRect().top + window.scrollY
      const rows = wrap.querySelectorAll('tbody tr')
      let rowH = ROW_FALLBACK
      for (const r of rows) {
        if (!r.querySelector('.tbl-empty')) { // 빈 목록 안내 행은 높이 측정에서 제외
          rowH = Math.max(r.getBoundingClientRect().height, 30)
          break
        }
      }
      const avail = window.innerHeight - top - PAGER_H - BOTTOM_GAP
      setSize(Math.max(min, Math.floor(avail / rowH)))
    }
    calc()
    window.addEventListener('resize', calc)
    return () => window.removeEventListener('resize', calc)
  })

  return size
}
