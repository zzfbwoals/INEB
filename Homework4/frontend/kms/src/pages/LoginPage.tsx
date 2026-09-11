import { Lock, User } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { isAxiosError } from 'axios'
import { login, type ApiEnvelope } from '@/api/auth'
import { setToken } from '@/lib/auth'
import { LogoFull } from '@/components/brand'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

export default function LoginPage() {
  const navigate = useNavigate()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const id = loginId.trim()
    if (!id || !password) {
      setError('아이디와 비밀번호를 입력해주세요.')
      return
    }
    setPending(true)
    setError(null)
    try {
      const result = await login(id, password)
      setToken(result.accessToken)
      navigate('/', { replace: true })
    } catch (err) {
      if (isAxiosError(err) && err.response) {
        const body = err.response.data as ApiEnvelope<unknown> | undefined
        setError(body?.message ?? '아이디 또는 비밀번호가 일치하지 않습니다.')
      } else {
        setError('서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.')
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="login-wrap">
      <svg className="keyglow" viewBox="0 0 100 140" fill="none">
        <circle cx="50" cy="38" r="32" fill="#3B9EFF" />
        <rect x="38" y="52" width="24" height="76" fill="#3B9EFF" />
      </svg>

      <div className="login-card">
        <div className="brand">
          <LogoFull width={86} height={26} letterFill="var(--text)" />
        </div>
        <h1>로그인</h1>
        <p className="sub">D'GuardKMS 통합키관리 어드민 콘솔</p>

        {error && <div className="login-err">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="lfield">
            <User size={15} />
            <Input
              id="loginId"
              placeholder="아이디"
              autoComplete="username"
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
            />
          </div>
          <div className="lfield">
            <Lock size={15} />
            <Input
              id="loginPw"
              type="password"
              placeholder="비밀번호"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <Button type="submit" className="login-btn" disabled={pending}>
            {pending ? '로그인 중…' : '로그인'}
          </Button>
        </form>
      </div>

      <div className="login-foot">CopyRight © 2008 INEB All Rights Reserved. · 192.168.200.52</div>
    </div>
  )
}
