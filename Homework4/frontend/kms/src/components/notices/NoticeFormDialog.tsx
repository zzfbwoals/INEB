import { Upload } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { createNotice, deleteNoticeFile, updateNotice, MAX_FILES, MAX_FILE_SIZE, type NoticeDetail, type NoticeFileItem } from '@/api/notices'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogBody, DialogContent, DialogFooter } from '@/components/ui/dialog'
import { errorMessage, useToast } from '@/components/ui/toast'
import { NoticeFileRow } from '@/components/notices/NoticeFileRow'

/* 목업 notices.html 등록 모달 — 등록/수정 공용(edit 프리필). 첨부는 드롭존으로 모아 multipart 로 한 번에 보내고
   서버가 마스터키로 암호화해 저장한다. 수정 시 기존 첨부의 [삭제]는 즉시 반영된다(DELETE /api/files/{id}). */
export function NoticeFormDialog({ edit, open, onClose, onDone }: {
  edit: NoticeDetail | null
  open: boolean
  onClose: () => void
  onDone: () => void
}) {
  const toast = useToast()
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [pinned, setPinned] = useState(false)
  const [existing, setExisting] = useState<NoticeFileItem[]>([])
  const [newFiles, setNewFiles] = useState<File[]>([])
  const [dragOver, setDragOver] = useState(false)
  const [pending, setPending] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  // 열릴 때(또는 다른 공지로 바뀔 때)만 초기화 — 상세 화면이 SSE 로 refetch 하면 edit 객체가 새로 오는데,
  // 그때마다 리셋하면 입력 중이던 제목·본문·선택 파일이 사라진다 (기존 첨부 목록은 여기서 직접 관리한다)
  const editId = edit?.id ?? null
  useEffect(() => {
    if (!open) return
    setTitle(edit?.title ?? '')
    setContent(edit?.content ?? '')
    setPinned(edit?.pinned ?? false)
    setExisting(edit?.files ?? [])
    setNewFiles([])
    setDragOver(false)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editId])

  function addFiles(list: FileList | File[]) {
    const incoming = Array.from(list)
    if (incoming.length === 0) return
    if (existing.length + newFiles.length + incoming.length > MAX_FILES) {
      toast(`첨부파일은 공지당 최대 ${MAX_FILES}개입니다`, 'error'); return
    }
    if (incoming.some((f) => f.size > MAX_FILE_SIZE)) { toast('파일당 20MB 이하만 첨부할 수 있습니다', 'error'); return }
    if (incoming.some((f) => f.size === 0)) { toast('빈 파일은 첨부할 수 없습니다', 'error'); return }
    setNewFiles((prev) => [...prev, ...incoming])
  }

  async function removeExisting(f: NoticeFileItem) {
    try {
      const { message } = await deleteNoticeFile(f.id)
      setExisting((prev) => prev.filter((x) => x.id !== f.id))
      toast(message ?? '첨부파일이 삭제되었습니다.')
    } catch (err) {
      toast(errorMessage(err), 'error')
    }
  }

  async function submit() {
    if (!title.trim()) { toast('제목을 입력해주세요', 'error'); return }
    if (!content.trim()) { toast('본문을 입력해주세요', 'error'); return }
    setPending(true)
    try {
      const form = { title: title.trim(), content, pinned, files: newFiles }
      const { message } = edit ? await updateNotice(edit.id, form) : await createNotice(form)
      toast(message ?? '저장되었습니다.')
      onClose()
      onDone()
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={edit ? '공지사항 수정' : '공지사항 등록'} wide>
        <DialogBody>
          <div className="field"><label>제목 <em>*</em></label>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="공지 제목을 입력하세요" /></div>
          <div className="field"><label>본문 <em>*</em></label>
            <textarea className="input txt" style={{ minHeight: 130 }} value={content} onChange={(e) => setContent(e.target.value)} placeholder="공지 내용을 입력하세요" /></div>
          <label className="ack">
            <input type="checkbox" checked={pinned} onChange={(e) => setPinned(e.target.checked)} />
            상단 고정
          </label>
          <div className="field">
            <label>첨부파일 <span style={{ fontWeight: 400, color: 'var(--text-3)' }}>— 마스터키 암호화 저장 · 파일당 20MB · 최대 {MAX_FILES}개</span></label>
            <div className={`dropzone${dragOver ? ' over' : ''}`} role="button" tabIndex={0}
              onClick={() => inputRef.current?.click()}
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); inputRef.current?.click() } }}
              onDragOver={(e) => { e.preventDefault(); setDragOver(true) }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => { e.preventDefault(); setDragOver(false); addFiles(e.dataTransfer.files) }}>
              <Upload size={15} />
              파일을 끌어오거나 클릭하여 선택
            </div>
            <input ref={inputRef} type="file" multiple hidden onChange={(e) => { if (e.target.files) addFiles(e.target.files); e.target.value = '' }} />
            {(existing.length > 0 || newFiles.length > 0) && (
              <div className="file-list" style={{ marginTop: 9 }}>
                {existing.map((f) => (
                  <NoticeFileRow key={`e${f.id}`} name={f.originalName} size={f.fileSize} meta={`enc_ver ${f.encVer}`}
                    action={<Button variant="ghost" size="sm" onClick={() => removeExisting(f)}>삭제</Button>} />
                ))}
                {newFiles.map((f, i) => (
                  <NoticeFileRow key={`n${i}-${f.name}`} name={f.name} size={f.size} meta="업로드 예정"
                    action={<Button variant="ghost" size="sm" onClick={() => setNewFiles((prev) => prev.filter((_, j) => j !== i))}>제거</Button>} />
                ))}
              </div>
            )}
          </div>
        </DialogBody>
        <DialogFooter>
          <Button disabled={pending} onClick={submit}>{edit ? '저장' : '등록'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
