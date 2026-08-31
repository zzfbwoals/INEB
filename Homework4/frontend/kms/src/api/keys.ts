import api from './axios'
import type { ApiEnvelope } from './auth'

/* ---- 백엔드 DTO 1:1 타입 (시각은 KST "yyyy-MM-dd HH:mm:ss" 문자열) ---- */
export type KeyState = 'PRE_ACTIVE' | 'ACTIVE' | 'DEACTIVATED' | 'DESTROYED'
export type KeyAlgorithm = 'AES' | 'ARIA' | 'LEA' | 'SEED' | 'RSA' | 'ECDSA' | 'SHA256' | 'SHA512'
export type KeyMode = 'CBC' | 'GCM' | 'CTR' | 'ECB'
export type KeyPurpose = 'ENC_DEC' | 'ENC_DEC_SIGN_VERIFY' | 'SIGN_VERIFY'
export type KeyAction = 'ACTIVATE' | 'REACTIVATE' | 'DEACTIVATE' | 'ROTATE' | 'DESTROY'
export type DeactivationTrigger = 'OPERATION' | 'INTEGRITY'
export type HistoryTrigger = 'OPERATION' | 'DATE_REACHED' | 'SCHEDULE' | 'INTEGRITY' | 'REACTIVATE' | 'ROTATE'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface KeySummary {
  keyUid: string
  keyName: string
  algorithm: KeyAlgorithm
  keySize: number
  mode: KeyMode | null
  purpose: KeyPurpose
  status: KeyState
  currentVersion: number
  activationDate: string
  versionCount: number
  scheduledVersion: number | null
  scheduledAt: string | null
  autoRotate: boolean
  rotationPeriodDays: number | null
  nextRotationAt: string
  integrityValid: boolean
}

export interface VersionInfo {
  version: number
  state: KeyState
  deactivationTrigger: DeactivationTrigger | null
  activationDate: string
  destroyedAt: string
  lastUsedAt: string | null
  usageCount: number
  integrityValid: boolean
  canEncrypt: boolean
  canDecrypt: boolean
}

export interface UsageStats {
  total: number
  encrypt: number
  decrypt: number
  sign: number
  verify: number
  oldVersion: number
  failed: number
}

export interface KeyDetail extends KeySummary {
  maxVersions: number
  description: string | null
  createdAt: string
  wrapAlgo: string
  publicKeyPem: string | null
  integrityHashShort: string | null
  versions: VersionInfo[]
  usageStats: UsageStats
}

export interface HistoryItem {
  version: number
  fromState: KeyState | null
  toState: KeyState
  reason: string
  trigger: HistoryTrigger
  changedBy: string
  changedAt: string
}

export interface UsageItem {
  version: number
  operation: 'ENCRYPT' | 'DECRYPT' | 'SIGN' | 'VERIFY'
  result: 'SUCCESS' | 'FAIL'
  failReason: string | null
  usedAt: string
  oldVersion: boolean
}

export interface UsageResponse {
  stats: UsageStats
  logs: PageResponse<UsageItem>
}

export interface KeyCreateRequest {
  keyName: string
  algorithm: KeyAlgorithm
  keySize: number
  mode: KeyMode | null
  purpose: KeyPurpose
  autoRotate: boolean
  rotationPeriodDays: number | null
  activationDate: string | null
  description: string | null
}

export interface KeyUpdateRequest {
  keyName: string
  description: string | null
  autoRotate: boolean
  rotationPeriodDays: number | null
  activationDate: string | null
}

export interface KeyActionRequest {
  action: KeyAction
  reason: string
  activationDate?: string | null
  version?: number | null
}

export interface KeyListParams {
  keyword?: string
  algorithm?: KeyAlgorithm | ''
  status?: 'LIVE' | 'ALL' | KeyState | ''
  purpose?: KeyPurpose | ''
  page?: number
  size?: number
  sort?: string
  direction?: 'asc' | 'desc'
}

/* ---- API ---- */
export async function listKeys(params: KeyListParams): Promise<PageResponse<KeySummary>> {
  const query: Record<string, string | number> = {}
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') query[k] = v
  }
  const res = await api.get<ApiEnvelope<PageResponse<KeySummary>>>('/api/keys', { params: query })
  return res.data.data
}

export async function getKey(keyUid: string): Promise<KeyDetail> {
  const res = await api.get<ApiEnvelope<KeyDetail>>(`/api/keys/${keyUid}`)
  return res.data.data
}

export async function createKey(body: KeyCreateRequest): Promise<KeyDetail> {
  const res = await api.post<ApiEnvelope<KeyDetail>>('/api/keys', body)
  return res.data.data
}

export async function updateKey(keyUid: string, body: KeyUpdateRequest): Promise<KeyDetail> {
  const res = await api.put<ApiEnvelope<KeyDetail>>(`/api/keys/${keyUid}`, body)
  return res.data.data
}

export async function runKeyAction(keyUid: string, body: KeyActionRequest): Promise<{ detail: KeyDetail; message: string | null }> {
  const res = await api.patch<ApiEnvelope<KeyDetail>>(`/api/keys/${keyUid}/status`, body)
  return { detail: res.data.data, message: res.data.message }
}

export async function getHistory(keyUid: string): Promise<HistoryItem[]> {
  const res = await api.get<ApiEnvelope<HistoryItem[]>>(`/api/keys/${keyUid}/history`)
  return res.data.data
}

export async function getUsage(keyUid: string, page = 0, size = 20): Promise<UsageResponse> {
  const res = await api.get<ApiEnvelope<UsageResponse>>(`/api/keys/${keyUid}/usage`, { params: { page, size } })
  return res.data.data
}

export interface MaterialReveal {
  version: number
  state: KeyState
  algorithm: KeyAlgorithm
  keySize: number
  material: string
  publicKey: string | null
  wrapAlgo: string
}

/** 버전 키값 조회 — 사유 필수, 감사로그 기록 */
export async function revealMaterial(keyUid: string, version: number, reason: string): Promise<{ data: MaterialReveal; message: string | null }> {
  const res = await api.post<ApiEnvelope<MaterialReveal>>(`/api/keys/${keyUid}/versions/${version}/material`, { reason })
  return { data: res.data.data, message: res.data.message }
}

export async function testEncrypt(keyUid: string, plaintext: string): Promise<{ ciphertext: string; version: number }> {
  const res = await api.post<ApiEnvelope<{ ciphertext: string; version: number }>>(`/api/keys/${keyUid}/test/encrypt`, { plaintext })
  return res.data.data
}

export async function testDecrypt(keyUid: string, ciphertext: string): Promise<{ plaintext: string; version: number; oldVersion: boolean }> {
  const res = await api.post<ApiEnvelope<{ plaintext: string; version: number; oldVersion: boolean }>>(`/api/keys/${keyUid}/test/decrypt`, { ciphertext })
  return res.data.data
}

export async function testSign(keyUid: string, plaintext: string): Promise<{ signature: string; version: number }> {
  const res = await api.post<ApiEnvelope<{ signature: string; version: number }>>(`/api/keys/${keyUid}/test/sign`, { plaintext })
  return res.data.data
}

export async function testVerify(keyUid: string, message: string, signature: string): Promise<{ valid: boolean; version: number; oldVersion: boolean }> {
  const res = await api.post<ApiEnvelope<{ valid: boolean; version: number; oldVersion: boolean }>>(`/api/keys/${keyUid}/test/verify`, { message, signature })
  return res.data.data
}
