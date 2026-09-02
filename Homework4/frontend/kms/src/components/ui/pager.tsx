/* 목록 공용 페이저 — 페이지가 많아도 최대 7개 안팎의 번호(1 … c-1 c c+1 … 끝)만 노출해
   페이저가 줄바꿈으로 높이를 잡아먹지 않게 한다 (자동 페이징의 전제) */

export function pageWindow(page: number, totalPages: number): (number | '…')[] {
  const last = Math.max(totalPages, 1) - 1
  if (last <= 6) return Array.from({ length: last + 1 }, (_, i) => i)
  const pages = [...new Set([0, page - 1, page, page + 1, last].filter((p) => p >= 0 && p <= last))].sort((a, b) => a - b)
  const out: (number | '…')[] = []
  let prev = -1
  for (const p of pages) {
    if (prev >= 0 && p - prev > 1) out.push('…')
    out.push(p)
    prev = p
  }
  return out
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
      {pageWindow(page, totalPages).map((p, i) => p === '…'
        ? <button key={`gap-${i}`} type="button" disabled>…</button>
        : <button key={p} type="button" className={p === page ? 'on' : ''} onClick={() => onPage(p)}>{p + 1}</button>)}
      <button type="button" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>›</button>
    </div>
  )
}
