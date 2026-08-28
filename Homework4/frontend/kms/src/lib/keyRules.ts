import type { KeyAlgorithm, KeyMode, KeyPurpose, KeyState, HistoryTrigger } from '@/api/keys'

/* 목업 data.js 의 규칙 이식 — 백엔드 KeyAlgorithm enum 과 동일해야 한다 */
export const ALGOS: Record<KeyAlgorithm, { sizes: number[]; modes: KeyMode[]; purpose: KeyPurpose; kind: 'SYMMETRIC' | 'ASYMMETRIC' | 'HMAC'; sizeLabel?: (s: number) => string }> = {
  AES: { sizes: [128, 192, 256], modes: ['CBC', 'GCM', 'CTR', 'ECB'], purpose: 'ENC_DEC', kind: 'SYMMETRIC' },
  ARIA: { sizes: [128, 192, 256], modes: ['CBC', 'GCM', 'CTR', 'ECB'], purpose: 'ENC_DEC', kind: 'SYMMETRIC' },
  LEA: { sizes: [128, 192, 256], modes: ['CBC', 'GCM', 'CTR', 'ECB'], purpose: 'ENC_DEC', kind: 'SYMMETRIC' },
  SEED: { sizes: [128], modes: ['CBC', 'ECB'], purpose: 'ENC_DEC', kind: 'SYMMETRIC' },
  RSA: { sizes: [2048, 3072, 4096], modes: [], purpose: 'ENC_DEC_SIGN_VERIFY', kind: 'ASYMMETRIC' },
  ECDSA: { sizes: [256, 384], modes: [], purpose: 'SIGN_VERIFY', kind: 'ASYMMETRIC', sizeLabel: (s) => `P-${s}` },
  SHA256: { sizes: [256], modes: [], purpose: 'SIGN_VERIFY', kind: 'HMAC' },
  SHA512: { sizes: [512], modes: [], purpose: 'SIGN_VERIFY', kind: 'HMAC' },
}

export const ALGO_GROUPS: { label: string; items: KeyAlgorithm[] }[] = [
  { label: '대칭키', items: ['AES', 'ARIA', 'LEA', 'SEED'] },
  { label: '비대칭키', items: ['RSA', 'ECDSA'] },
  { label: 'HMAC', items: ['SHA256', 'SHA512'] },
]

export const STATE_KO: Record<KeyState, string> = { PRE_ACTIVE: '준비', ACTIVE: '운영', DEACTIVATED: '정지', DESTROYED: '폐기' }
export const BADGE: Record<KeyState, string> = { PRE_ACTIVE: 'b-pre', ACTIVE: 'b-active', DEACTIVATED: 'b-deact', DESTROYED: 'b-destroyed' }
export const PURPOSE_KO: Record<KeyPurpose, string> = {
  ENC_DEC: '암/복호화',
  ENC_DEC_SIGN_VERIFY: '암/복호화 및 서명/검증',
  SIGN_VERIFY: '서명/검증',
}
export const TRIGGER_KO: Record<HistoryTrigger, string> = {
  OPERATION: '관리자',
  DATE_REACHED: 'SYSTEM 활성일',
  SCHEDULE: 'SYSTEM 갱신주기',
  INTEGRITY: 'SYSTEM 무결성',
  REACTIVATE: '재활성화',
  ROTATE: '갱신',
}
export const PURPOSE_HELP: Record<KeyPurpose, string> = {
  ENC_DEC: '대칭키 — 암호화·복호화 전용',
  ENC_DEC_SIGN_VERIFY: 'RSA — 암복호화와 서명/검증 모두 가능',
  SIGN_VERIFY: '서명/검증 전용 (ECDSA · HMAC)',
}

export const ROT_MIN = 1
export const ROT_MAX = 730
export const ROT_DEFAULT = 90
export const MAX_VERSIONS = 100

export const canEncrypt = (p: KeyPurpose) => p !== 'SIGN_VERIFY'
export const canSign = (p: KeyPurpose) => p !== 'ENC_DEC'

/** 목업 표기 "AES-256", "ECDSA-P256", "HMAC-SHA256" */
export function algoLabel(algorithm: KeyAlgorithm, keySize: number): string {
  if (algorithm === 'SHA256' || algorithm === 'SHA512') return `HMAC-${algorithm}`
  if (algorithm === 'ECDSA') return `ECDSA-P${keySize}`
  return `${algorithm}-${keySize}`
}

/** RSA-OAEP(SHA-256) 평문 상한 바이트 — 그 외 알고리즘은 null */
export function maxPlaintextBytes(algorithm: KeyAlgorithm, keySize: number): number | null {
  return algorithm === 'RSA' ? keySize / 8 - 66 : null
}

export function utf8Bytes(text: string): number {
  return new TextEncoder().encode(text).length
}
