import type { ReactNode } from 'react'
import { fmtBytes } from '@/lib/format'

/* 목업 notice-detail.html 의 .file-it — 상세(다운로드)·등록/수정 모달(삭제·제거) 공용 첨부 행 */
export function NoticeFileRow({ name, size, meta, action }: { name: string; size: number; meta?: string; action?: ReactNode }) {
  return (
    <div className="file-it">
      <span className="fic">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M6 3h8l5 5v13H6V3z" /><path d="M14 3v5h5" /></svg>
      </span>
      <div className="fn">
        <b title={name}>{name}</b>
        <span>{fmtBytes(size)}{meta && ` · ${meta}`}</span>
      </div>
      {action}
    </div>
  )
}
