import { Download, Pencil, Pin, ShieldCheck, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import AppLayout from '@/components/layout/AppLayout'
import { downloadNoticeFile, getNotice, type NoticeDetail, type NoticeFileItem } from '@/api/notices'
import { fmt } from '@/lib/format'
import { subscribeUiEvents } from '@/lib/events'
import { errorMessage, useToast } from '@/components/ui/toast'
import { NoticeFileRow } from '@/components/notices/NoticeFileRow'
import { NoticeFormDialog } from '@/components/notices/NoticeFormDialog'
import { NoticeDeleteDialog } from '@/components/notices/NoticeDeleteDialog'

/* 목업 notice-detail.html — 공지 상세. 최초 진입 1회만 조회수 +1(countView), 실시간·수정 후 재조회는 조회수를 올리지 않는다 */
export default function NoticeDetailPage() {
  const { id = '' } = useParams()
  const noticeId = Number(id)
  const navigate = useNavigate()
  const toast = useToast()
  const [detail, setDetail] = useState<NoticeDetail | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [downloading, setDownloading] = useState<number | null>(null)
  // 조회수를 올린 공지 id — dev StrictMode 의 이펙트 재실행·같은 화면 재조회에서 두 번 올리지 않기 위한 가드
  const countedFor = useRef<number | null>(null)

  const load = useCallback(async (countView: boolean) => {
    try {
      setDetail(await getNotice(noticeId, countView))
    } catch (err) {
      toast(errorMessage(err), 'error')
      navigate('/notices', { replace: true })
    }
  }, [noticeId, navigate, toast])

  useEffect(() => {
    const countView = countedFor.current !== noticeId
    countedFor.current = noticeId
    load(countView)
  }, [load, noticeId])

  // 실시간 갱신 — 이 공지를 대상으로 한 수정·첨부 삭제는 refetch, 삭제는 목록으로.
  // DB 직접 수정(DB_DIRECT_CHANGE, 이 공지 또는 NOTICE#* 대량 변경)도 refetch — 행이 지워졌으면 load 가 404 로 목록으로 보낸다
  useEffect(() => {
    return subscribeUiEvents((e) => {
      const mine = e.target === `NOTICE#${noticeId}`
      if (e.action === 'DB_DIRECT_CHANGE' && (mine || e.target === 'NOTICE#*')) { load(false); return }
      if (!mine) return
      if (e.action === 'NOTICE_DELETED') {
        toast('공지사항이 삭제되었습니다.')
        navigate('/notices', { replace: true })
      } else if (e.action === 'NOTICE_UPDATED' || e.action === 'NOTICE_FILE_DELETED') {
        load(false)
      }
    })
  }, [noticeId, load, navigate, toast])

  async function download(f: NoticeFileItem) {
    setDownloading(f.id)
    try {
      await downloadNoticeFile(f.id, f.originalName)
      toast('다운로드를 시작합니다.')
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setDownloading(null)
    }
  }

  if (!detail) {
    return <AppLayout><div className="tbl-empty">불러오는 중…</div></AppLayout>
  }

  return (
    <AppLayout>
      {/* 목록 복귀는 브라우저 뒤로가기 — 헤더 없음. 감사 로그·수정·삭제는 메타 줄 우측 아이콘 */}
      <div className="card notice-view">
        <h2>{detail.pinned && <span className="pin-mark" data-tip="상단 고정"><Pin size={16} /></span>}{detail.title}</h2>
        <div className="nmeta">
          <span>번호 <b className="mono">{detail.id}</b></span>
          <span>작성자 <b>{detail.authorName}</b></span>
          <span>등록 <span className="mono">{fmt(detail.createdAt)}</span></span>
          {detail.updatedAt !== detail.createdAt && <span>수정 <span className="mono">{fmt(detail.updatedAt)}</span></span>}
          <span>조회수 <b>{detail.viewCount.toLocaleString()}</b></span>
          <span className="nacts">
            <Link className="icon-btn" data-tip="감사 로그" aria-label="감사 로그" to={`/audit?target=${encodeURIComponent(`NOTICE#${detail.id}`)}`}>
              <ShieldCheck size={15} />
            </Link>
            <button type="button" className="icon-btn" data-tip="수정" aria-label="수정" onClick={() => setEditOpen(true)}>
              <Pencil size={15} />
            </button>
            <button type="button" className="icon-btn danger" data-tip="삭제" aria-label="삭제" onClick={() => setDeleteOpen(true)}>
              <Trash2 size={15} />
            </button>
          </span>
        </div>
        <div className="nbody">{detail.content}</div>
        {detail.files.length > 0 && (
          <div className="file-list">
            <div className="file-list-h">첨부파일</div>
            {detail.files.map((f) => (
              <NoticeFileRow key={f.id} name={f.originalName} size={f.fileSize} meta={`enc_ver ${f.encVer}`}
                action={
                  <button type="button" className="icon-btn" data-tip="다운로드" aria-label="다운로드" disabled={downloading === f.id} onClick={() => download(f)}>
                    <Download size={16} />
                  </button>
                } />
            ))}
          </div>
        )}
      </div>

      <NoticeFormDialog edit={detail} open={editOpen} onClose={() => setEditOpen(false)} onDone={() => load(false)} />
      <NoticeDeleteDialog notice={detail} open={deleteOpen} onClose={() => setDeleteOpen(false)} />
    </AppLayout>
  )
}
