import api from './axios'

/** 백엔드 공통 응답 포맷 */
export interface ApiEnvelope<T> {
  success: boolean
  data: T
  message: string | null
  errorCode: string | null
}

export interface LoginResult {
  accessToken: string
  name: string
  role: string
}

export interface Me {
  loginId: string
  name: string
  role: string
}

export async function login(loginId: string, password: string): Promise<LoginResult> {
  const res = await api.post<ApiEnvelope<LoginResult>>('/api/auth/login', { loginId, password })
  return res.data.data
}

export async function logout(): Promise<void> {
  try {
    await api.post('/api/auth/logout')
  } catch {
    // 서버 응답과 무관하게 클라이언트 토큰 폐기가 우선
  }
}

export async function fetchMe(): Promise<Me> {
  const res = await api.get<ApiEnvelope<Me>>('/api/auth/me')
  return res.data.data
}
