import { ChevronDown, ChevronUp } from 'lucide-react'
/* 목록 헤더 정렬 표시 — 정렬 가능한 열은 호버로만 알리고, 실제 정렬 중인 열에만 방향 화살표를 띄운다 */

export type SortState = { field: string; dir: 'asc' | 'desc' } | null

/** th className — 정렬 가능(sortable) + 현재 정렬 열(on) */
export function sortClass(sort: SortState, field: string): string {
  return sort?.field === field ? 'sortable on' : 'sortable'
}

/** 현재 정렬 열에만 오름차순(▲)·내림차순(▼) 화살표를 표시 */
export function SortMark({ sort, field }: { sort: SortState; field: string }) {
  if (sort?.field !== field) return null
  return (
    sort.dir === 'asc'
      ? <ChevronUp className="sort-mark" size={11} strokeWidth={2.5} aria-label="오름차순" />
      : <ChevronDown className="sort-mark" size={11} strokeWidth={2.5} aria-label="내림차순" />
  )
}
