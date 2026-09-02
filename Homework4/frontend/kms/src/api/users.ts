import api from './axios'
import type { ApiEnvelope } from './auth'
import type { PageResponse } from './keys'

export type UserStatus = 'ACTIVE' | 'SUSPENDED'

export interface UserSummary {
  id: number
  name: string
  phoneMasked: string
  emailMasked: string
  status: UserStatus
  encVer: number
  integrityValid: boolean
  createdAt: string
  updatedAt: string
}

export interface UserCreateRequest {
  name: string
  phone: string
  email: string
  password: string
  status?: UserStatus
}

export interface UserUpdateRequest {
  name: string
  phone: string
  email: string
  status: UserStatus
  password?: string | null
}

export interface UserPlain {
  id: number
  name: string
  phone: string
  email: string
}

export interface UserListParams {
  keyword?: string
  phone?: string
  email?: string
  status?: UserStatus | ''
  page?: number
  size?: number
  sort?: string
  direction?: 'asc' | 'desc'
}

export async function listUsers(params: UserListParams): Promise<PageResponse<UserSummary>> {
  const query: Record<string, string | number> = {}
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') query[k] = v
  }
  const res = await api.get<ApiEnvelope<PageResponse<UserSummary>>>('/api/users', { params: query })
  return res.data.data
}

export async function createUser(body: UserCreateRequest): Promise<{ data: UserSummary; message: string | null }> {
  const res = await api.post<ApiEnvelope<UserSummary>>('/api/users', body)
  return { data: res.data.data, message: res.data.message }
}

export async function updateUser(id: number, body: UserUpdateRequest): Promise<{ data: UserSummary; message: string | null }> {
  const res = await api.put<ApiEnvelope<UserSummary>>(`/api/users/${id}`, body)
  return { data: res.data.data, message: res.data.message }
}

/** 개인정보 원문 조회 — ADMIN 한정, 사유 필수, USER_PLAIN_VIEWED 감사 기록 */
export async function viewUserPlain(id: number, reason: string): Promise<{ data: UserPlain; message: string | null }> {
  const res = await api.post<ApiEnvelope<UserPlain>>(`/api/users/${id}/plain`, { reason })
  return { data: res.data.data, message: res.data.message }
}
