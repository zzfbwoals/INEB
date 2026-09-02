/* 목록 공용 페이저 — 5페이지 고정 블록 방식. 현재 페이지가 속한 블록(1~5, 6~10, …)의 번호만 보여주고
   ‹ › 로 페이지를 넘기면 블록 경계에서 자동으로 다음 블록이 표시된다. */

const BLOCK = 5

export function pageBlock(page: number, totalPages: number): number[] {
  const last = Math.max(totalPages, 1) - 1
  const start = Math.floor(page / BLOCK) * BLOCK
  const end = Math.min(start + BLOCK - 1, last)
  return Array.from({ length: end - start + 1 }, (_, i) => start + i)
}

export function Pager({ page, data, unit = '건', onPage }: {
  page: number
  data: { totalElements: number; totalPages: number } | null
  unit?: string
  onPage: (p: number) => void
}) {
  const totalPages = Math.max(data?.totalPages ?? 1, 1)
  return (
    <div className="pager">
      <span className="pinfo">총 {data?.totalElements ?? 0}{unit} · {page + 1}/{totalPages} 페이지</span>
      <button type="button" disabled={page === 0} onClick={() => onPage(page - 1)}>‹</button>
      {pageBlock(page, totalPages).map((p) => (
        <button key={p} type="button" className={p === page ? 'on' : ''} onClick={() => onPage(p)}>{p + 1}</button>
      ))}
      <button type="button" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>›</button>
    </div>
  )
}
