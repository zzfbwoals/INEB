import { isAxiosError } from 'axios'
import api from './axios'
import type { ApiEnvelope } from './auth'
import type { PageResponse } from './keys'

/* 공지사항 게시판 — 첨부파일은 서버가 마스터키(AES-256-GCM)로 암호화해 DB 에 저장하고 다운로드 시에만 복호화한다 */

export type NoticeScope = 'TITLE_CONTENT' | 'TITLE' | 'AUTHOR'

export interface NoticeSummary {
  id: number
  title: string
  pinned: boolean
  authorName: string
  viewCount: number
  fileCount: number
  createdAt: string
}

export interface NoticeFileItem {
  id: number
  originalName: string
  fileSize: number
  encVer: number
  createdAt: string
}

export interface NoticeDetail {
  id: number
  title: string
  content: string
  pinned: boolean
  createdBy: string
  authorName: string
  viewCount: number
  files: NoticeFileItem[]
  createdAt: string
  updatedAt: string
}

export interface NoticeListParams {
  keyword?: string
  scope?: NoticeScope
  pinned?: 'true' | 'false' | ''
  page?: number
  size?: number
  sort?: string
  direction?: 'asc' | 'desc'
}

export interface NoticeForm {
  title: string
  content: string
  pinned: boolean
  /** 새로 추가할 첨부 — 수정 시 기존 첨부는 유지되고 삭제는 deleteNoticeFile 로 */
  files: File[]
}

/** 서버 정책과 동일 (NoticeService.MAX_FILE_SIZE / MAX_FILES) — 업로드 전에 화면에서 먼저 거른다 */
export const MAX_FILE_SIZE = 20 * 1024 * 1024
export const MAX_FILES = 5

export async function listNotices(params: NoticeListParams): Promise<PageResponse<NoticeSummary>> {
  const query: Record<string, string | number> = {}
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') query[k] = v
  }
  const res = await api.get<ApiEnvelope<PageResponse<NoticeSummary>>>('/api/notices', { params: query })
  return res.data.data
}

/** countView=false 는 실시간 재조회·수정 후 재조회용 — 조회수를 올리지 않는다 */
export async function getNotice(id: number, countView = true): Promise<NoticeDetail> {
  const res = await api.get<ApiEnvelope<NoticeDetail>>(`/api/notices/${id}`, { params: { countView } })
  return res.data.data
}

/* multipart — 파트 이름은 서버 NoticeForm 과 동일(title / content / pinned / files 반복).
   axios 는 FormData 를 보내면 브라우저가 boundary 를 붙이도록 Content-Type 을 넘긴다 (인스턴스 기본값 json 을 덮어쓴다) */
function toFormData(form: NoticeForm): FormData {
  const fd = new FormData()
  fd.append('title', form.title)
  fd.append('content', form.content)
  fd.append('pinned', String(form.pinned))
  for (const f of form.files) fd.append('files', f)
  return fd
}
const MULTIPART = { headers: { 'Content-Type': 'multipart/form-data' } }

export async function createNotice(form: NoticeForm): Promise<{ data: NoticeDetail; message: string | null }> {
  const res = await api.post<ApiEnvelope<NoticeDetail>>('/api/notices', toFormData(form), MULTIPART)
  return { data: res.data.data, message: res.data.message }
}

export async function updateNotice(id: number, form: NoticeForm): Promise<{ data: NoticeDetail; message: string | null }> {
  const res = await api.put<ApiEnvelope<NoticeDetail>>(`/api/notices/${id}`, toFormData(form), MULTIPART)
  return { data: res.data.data, message: res.data.message }
}

export async function deleteNotice(id: number): Promise<{ message: string | null }> {
  const res = await api.delete<ApiEnvelope<void>>(`/api/notices/${id}`)
  return { message: res.data.message }
}

export async function deleteNoticeFile(fileId: number): Promise<{ message: string | null }> {
  const res = await api.delete<ApiEnvelope<void>>(`/api/files/${fileId}`)
  return { message: res.data.message }
}

/** 복호화 다운로드 — JWT 헤더가 필요하므로 blob 으로 받아 원본 파일명으로 저장을 트리거한다 (NOTICE_FILE_DOWNLOADED 감사 기록) */
export async function downloadNoticeFile(fileId: number, originalName: string): Promise<void> {
  let res
  try {
    res = await api.get<Blob>(`/api/files/${fileId}/download`, { responseType: 'blob' })
  } catch (err) {
    await unwrapBlobError(err)
  }
  const url = URL.createObjectURL(res!.data)
  const a = document.createElement('a')
  a.href = url
  a.download = originalName
  a.click()
  URL.revokeObjectURL(url)
}

/** blob 요청의 오류 본문도 Blob 이라 errorMessage 가 서버 메시지를 읽지 못한다 — JSON 으로 되돌린 뒤 다시 던진다 */
async function unwrapBlobError(err: unknown): Promise<never> {
  if (isAxiosError(err) && err.response && err.response.data instanceof Blob) {
    try {
      err.response.data = JSON.parse(await err.response.data.text())
    } catch {
      /* 본문이 JSON 이 아니면(예: Nginx 413 HTML) 그대로 둔다 — errorMessage 의 기본 문구로 표시된다 */
    }
  }
  throw err
}
