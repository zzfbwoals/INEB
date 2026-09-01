import { useEffect, useState } from 'react'
import { createUser, updateUser, viewUserPlain, type UserStatus, type UserSummary } from '@/api/users'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogBody, DialogContent, DialogFooter } from '@/components/ui/dialog'
import { errorMessage, useToast } from '@/components/ui/toast'

/* 목업 사용자 등록/수정 모달 — 등록: 초기 비밀번호 필수 / 수정: "비밀번호 재설정" 토글로만 입력 노출.
   수정 폼의 연락처·이메일은 원문 조회 API로 미리 채운다 (감사 기록됨, ADMIN 아니면 빈 값). */
export function UserFormDialog({ edit, open, onClose, onDone }: {
  edit: UserSummary | null
  open: boolean
  onClose: () => void
  onDone: () => void
}) {
  const toast = useToast()
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [status, setStatus] = useState<UserStatus>('ACTIVE')
  const [password, setPassword] = useState('')
  const [password2, setPassword2] = useState('')
  const [pwReset, setPwReset] = useState(false)
  const [pending, setPending] = useState(false)

  useEffect(() => {
    if (!open) return
    setName(edit?.name ?? '')
    setPhone('')
    setEmail('')
    setStatus(edit?.status ?? 'ACTIVE')
    setPassword('')
    setPassword2('')
    setPwReset(false)
    if (edit) {
      // 수정 폼에는 현재 연락처·이메일 원문을 미리 채운다 — 원문 노출이므로 감사로그(USER_PLAIN_VIEWED)에 남는다.
      // ADMIN이 아니면(403) 빈 값으로 두고 직접 입력받는다.
      viewUserPlain(edit.id, '사용자 정보 수정 화면 조회')
        .then(({ data }) => { setPhone(data.phone); setEmail(data.email) })
        .catch(() => {})
    }
  }, [open, edit])

  const needPassword = !edit || pwReset

  async function submit() {
    if (!name.trim()) { toast('이름을 입력해주세요', 'error'); return }
    if (!/^01[016789]-\d{3,4}-\d{4}$/.test(phone)) { toast('연락처는 010-0000-0000 형식이어야 합니다', 'error'); return }
    if (!email.trim()) { toast('이메일을 입력해주세요', 'error'); return }
    if (needPassword) {
      if (password.length < 8 || [...password].every((c) => /[A-Za-z0-9]/.test(c))) {
        toast('비밀번호는 8자 이상이며 특수문자를 포함해야 합니다', 'error'); return
      }
      if (password !== password2) { toast('비밀번호가 일치하지 않습니다', 'error'); return }
    }
    setPending(true)
    try {
      const { message } = edit
        ? await updateUser(edit.id, { name: name.trim(), phone, email: email.trim(), status, password: pwReset ? password : null })
        : await createUser({ name: name.trim(), phone, email: email.trim(), password, status })
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
      <DialogContent title={edit ? '사용자 수정' : '사용자 등록'}>
        <DialogBody>
          <div className="field"><label>이름 <em>*</em></label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="홍길동" /></div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
            <div className="field"><label>연락처 <em>*</em></label>
              <Input className="mono" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="010-0000-0000" /></div>
            <div className="field"><label>상태</label>
              <select className="input" value={status} onChange={(e) => setStatus(e.target.value as UserStatus)}>
                <option value="ACTIVE">활성</option>
                <option value="SUSPENDED">정지</option>
              </select></div>
          </div>
          <div className="field"><label>이메일 <em>*</em></label>
            <Input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="user@ineb.co.kr" /></div>
          {needPassword && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <div className="field"><label>{edit ? '새 비밀번호' : '초기 비밀번호'} <em>*</em></label>
                <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="8자 이상, 특수문자 포함" /></div>
              <div className="field"><label>비밀번호 확인 <em>*</em></label>
                <Input type="password" value={password2} onChange={(e) => setPassword2(e.target.value)} placeholder="비밀번호 재입력" /></div>
            </div>
          )}
        </DialogBody>
        <DialogFooter>
          {edit && (
            <Button variant="ghost" onClick={() => { setPwReset((v) => !v); setPassword(''); setPassword2('') }}>
              {pwReset ? '재설정 취소' : '비밀번호 재설정'}
            </Button>
          )}
          <Button variant="ghost" onClick={onClose}>취소</Button>
          <Button disabled={pending} onClick={submit}>저장</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
