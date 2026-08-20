import axios from 'axios'
import { clearToken, getToken } from '@/lib/auth'

// 실행 환경별 baseURL — dev: http://localhost:8080, prod: 빈 값(같은 출처, Nginx /api 프록시)
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(undefined, (error) => {
  // 토큰 만료/무효 — 로그인 요청 자체의 401은 화면에서 처리
  const status: number | undefined = error.response?.status
  const url: string = error.config?.url ?? ''
  if (status === 401 && !url.includes('/api/auth/login')) {
    clearToken()
    if (window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
  }
  return Promise.reject(error)
})

export default api
