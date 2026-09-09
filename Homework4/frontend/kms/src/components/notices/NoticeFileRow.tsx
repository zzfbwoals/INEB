import { File } from 'lucide-react'
import type { ReactNode } from 'react'
import { fmtBytes } from '@/lib/format'

/* 목업 notice-detail.html 의 .file-it — 상세(다운로드)·등록/수정 모달(삭제·제거) 공용 첨부 행 */
export function NoticeFileRow({ name, size, meta, action }: { name: string; size: number; meta?: string; action?: ReactNode }) {
  return (
    <div className="file-it">
      <span className="fic">
        <File size={15} />
      </span>
      <div className="fn">
        <b title={name}>{name}</b>
        <span>{fmtBytes(size)}{meta && ` · ${meta}`}</span>
      </div>
      {action}
    </div>
  )
}
