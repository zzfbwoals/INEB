import { useLayoutEffect, useState, type RefObject } from 'react'

/* 목록 자동 페이징 — 행이 화면(뷰포트)을 벗어나지 않도록 남은 높이로 페이지 크기를 계산한다.
   행 높이는 화면별 고정 상수를 받는다 — 렌더된 행을 실측하면 데이터 도착 전(폴백)과 후(실측)의
   값 차이로 총 페이지 수가 한 번 출렁이므로, 계산을 결정적으로 만들기 위해 상수를 쓴다.
   (행 높이 상수: 감사 로그 46 / 키 목록 50 / 사용자 목록 58 — 셀 내용에 따라 다름) */

const PAGER_H = 56 // .pager 높이
const BOTTOM_GAP = 60 // .content 하단 여백 — 초과하면 페이지 스크롤이 생긴다

/** tbl-wrap 요소 ref 와 화면별 행 높이를 받아 페이지 크기를 돌려준다. 0 이면 아직 미계산 — fetch 를 보류할 것 */
export function useAutoPageSize(wrapRef: RefObject<HTMLDivElement | null>, rowHeight: number, min = 3): number {
  const [size, setSize] = useState(0)

  // 의존성 없이 렌더마다 재계산 — 입력(뷰포트 높이·테이블 위치)이 같으면 값이 같아 리렌더 없음
  useLayoutEffect(() => {
    function calc() {
      const wrap = wrapRef.current
      if (!wrap) return
      const body = wrap.querySelector('tbody')
      const top = (body ?? wrap).getBoundingClientRect().top + window.scrollY
      const avail = window.innerHeight - top - PAGER_H - BOTTOM_GAP
      setSize(Math.max(min, Math.floor(avail / rowHeight)))
    }
    calc()
    window.addEventListener('resize', calc)
    return () => window.removeEventListener('resize', calc)
  })

  return size
}
