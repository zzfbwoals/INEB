import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import AppLayout from '@/components/layout/AppLayout'
import { getKey, listKeys, testDecrypt, testEncrypt, testSign, testVerify, type KeyDetail, type KeySummary, type VersionInfo } from '@/api/keys'
import { PURPOSE_KO, STATE_KO, algoLabel, canEncrypt, canSign, maxPlaintextBytes, utf8Bytes } from '@/lib/keyRules'
import { Button } from '@/components/ui/button'
import { errorMessage, useToast } from '@/components/ui/toast'
import { StateBadge } from '@/components/keys/StateBadge'

type Mode = 'enc' | 'sig'
const DEFAULT_PLAIN = '고객 카드번호 테스트 데이터 4111-1111-1111-1111'

/* 목업 test.html — 암복호화·서명검증 테스트 */
export default function KeyTestPage() {
  const [params] = useSearchParams()
  const toast = useToast()
  const [keys, setKeys] = useState<KeySummary[]>([])
  const [selectedUid, setSelectedUid] = useState(params.get('id') ?? '')
  const [detail, setDetail] = useState<KeyDetail | null>(null)
  const [mode, setMode] = useState<Mode>('enc')
  const [input, setInput] = useState(DEFAULT_PLAIN)
  const [encOut, setEncOut] = useState<string | null>(null)
  const [decIn, setDecIn] = useState('')
  const [decMsg, setDecMsg] = useState('')
  const [decOut, setDecOut] = useState<{ text: string; warn?: string } | null>(null)
  const [pending, setPending] = useState(false)

  useEffect(() => {
    listKeys({ status: 'ALL', size: 100, sort: 'keyName', direction: 'asc' })
      .then((res) => {
        setKeys(res.content)
        if (!selectedUid) {
          const first = res.content.find((k) => k.status !== 'DESTROYED')
          if (first) setSelectedUid(first.keyUid)
        }
      })
      .catch((err) => toast(errorMessage(err), 'error'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!selectedUid) { setDetail(null); return }
    let cancelled = false
    getKey(selectedUid)
      .then((d) => {
        if (cancelled) return
        setDetail(d)
        setMode(canEncrypt(d.purpose) ? 'enc' : 'sig')
        setEncOut(null); setDecOut(null); setDecIn('')
        if (d.status !== 'ACTIVE') toast(`${d.keyName}은(는) ${STATE_KO[d.status]} 상태 — 호출이 차단됩니다`, 'error')
      })
      .catch((err) => { if (!cancelled) toast(errorMessage(err), 'error') })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedUid])

  const sig = mode === 'sig'
  const current = detail?.versions.find((v) => v.version === detail.currentVersion)
  const curOk = !!current && current.state === 'ACTIVE'
  const opL = sig ? '서명' : '암호화', opR = sig ? '검증' : '복호화'
  const maxBytes = detail && !sig ? maxPlaintextBytes(detail.algorithm, detail.keySize) : null
  const inputBytes = utf8Bytes(input)
  const tooLong = maxBytes !== null && inputBytes > maxBytes

  const parsed = parseInput(decIn, sig, detail?.versions ?? [], detail?.currentVersion ?? 0)

  function switchMode(m: Mode) {
    setMode(m); setEncOut(null); setDecOut(null); setDecIn('')
  }

  async function runLeft() {
    if (!detail || !curOk) { toast('최신 버전이 ACTIVE가 아니어서 차단됩니다 (400)', 'error'); return }
    if (tooLong) { toast(`평문이 ${maxBytes}바이트를 초과했습니다 (400)`, 'error'); return }
    setPending(true)
    try {
      if (sig) {
        const r = await testSign(detail.keyUid, input)
        setEncOut(r.signature)
        setDecIn(r.signature); setDecMsg(input)
      } else {
        const r = await testEncrypt(detail.keyUid, input)
        setEncOut(r.ciphertext)
        setDecIn(r.ciphertext)
      }
      setDecOut(null)
      toast(`${opL} 성공 — 결과가 ${opR} 입력에 자동 채워졌습니다`)
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setPending(false)
    }
  }

  async function runRight() {
    if (!detail) return
    if (!decIn.trim()) { toast('입력이 필요합니다', 'error'); return }
    if (parsed.kind === 'error') { toast(`400 — ${parsed.message}`, 'error'); return }
    setPending(true)
    try {
      if (sig) {
        const r = await testVerify(detail.keyUid, decMsg, decIn.trim())
        setDecOut({
          text: `verified: ${r.valid}`,
          warn: r.oldVersion ? 'ⓘ 구 버전으로 처리됨' : undefined,
        })
        toast(r.valid ? '서명 검증 성공' : '서명 불일치 — 검증 실패', r.valid ? 'ok' : 'error')
      } else {
        const r = await testDecrypt(detail.keyUid, decIn.trim())
        setDecOut({
          text: r.plaintext,
          warn: r.oldVersion ? 'ⓘ 구 버전으로 처리됨 — 재암호화 대상 데이터입니다' : undefined,
        })
        toast(`복호화 성공 — v${r.version}${r.oldVersion ? ' (구 버전)' : ''}`)
      }
    } catch (err) {
      setDecOut(null)
      toast(errorMessage(err), 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <AppLayout crumb="키 관리 / 동작 테스트">
      <div className="page-h">
        <div><h2>동작 테스트</h2></div>
      </div>

      <div className="filters">
        <div className="field" style={{ minWidth: 320 }}>
          <label>대상 키 선택</label>
          <select className="input" value={selectedUid} onChange={(e) => setSelectedUid(e.target.value)}>
            {keys.length === 0 && <option value="">등록된 키가 없습니다</option>}
            {keys.map((k) => (
              <option key={k.keyUid} value={k.keyUid} disabled={k.status === 'DESTROYED'}>
                {k.keyName} · {algoLabel(k.algorithm, k.keySize)}{k.mode ? '/' + k.mode : ''} · {PURPOSE_KO[k.purpose]} · {STATE_KO[k.status]} · v{k.currentVersion}{k.status === 'DESTROYED' ? ' — 테스트 불가' : ''}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>작업</label>
          <div className="seg">
            <button type="button" className={mode === 'enc' ? 'on' : ''} disabled={!detail || !canEncrypt(detail.purpose)} onClick={() => switchMode('enc')}>암/복호화</button>
            <button type="button" className={mode === 'sig' ? 'on' : ''} disabled={!detail || !canSign(detail.purpose)} onClick={() => switchMode('sig')}>서명/검증</button>
          </div>
        </div>
        {detail && <div className="keyinfo"><StateBadge state={detail.status} /></div>}
      </div>

      <div className="test-grid">
        <div className="card test-card">
          <div className="card-h"><h3>{sig ? '서명 생성' : '평문 암호화'}</h3><span className="hint" /></div>
          <div className="body">
            <div className="field">
              <label>{sig ? '원문 입력' : '평문 입력'}</label>
              <textarea className="input" value={input} onChange={(e) => setInput(e.target.value)} placeholder="입력하세요" />
              {maxBytes !== null && (
                <div className="help" style={{ color: tooLong ? 'var(--red)' : undefined }}>
                  RSA-{detail!.keySize}는 최대 {maxBytes}바이트까지 암호화 가능 (현재 {inputBytes}B){tooLong ? ' — 초과' : ''}. 실무에서는 RSA로 데이터를 직접 암호화하지 않고 대칭키만 봉인합니다.
                </div>
              )}
            </div>
            <Button style={{ alignSelf: 'flex-start' }} disabled={!curOk || tooLong || pending} onClick={runLeft}>{sig ? '서명 실행' : '암호화 실행'}</Button>
            <div className="field">
              <label>{sig ? '서명값' : '암호문'}</label>
              <div className={`result-box ${encOut ? 'ok' : ''}`}>
                {encOut ?? '결과가 여기에 표시됩니다'}
              </div>
            </div>
          </div>
        </div>

        <div className="card test-card">
          <div className="card-h"><h3>{sig ? '서명 검증' : '암호문 복호화'}</h3><span className="hint" /></div>
          <div className="body">
            {sig && (
              <div className="field">
                <label>원문</label>
                <textarea className="input" value={decMsg} onChange={(e) => setDecMsg(e.target.value)} placeholder="서명 대상 원문" />
              </div>
            )}
            <div className="field">
              <label>{sig ? '서명값' : '암호문'}</label>
              <textarea className="input" value={decIn} onChange={(e) => setDecIn(e.target.value)} placeholder={sig ? 'version:signature 형식' : 'version:iv:ciphertext 형식'} />
            </div>
            <Button style={{ alignSelf: 'flex-start' }} disabled={pending} onClick={runRight}>{sig ? '검증 실행' : '복호화 실행'}</Button>
            <div className="field">
              <label>{sig ? '검증 결과' : '복호화 결과'}</label>
              <div className={`result-box ${decOut ? 'ok' : ''}`}>
                {decOut ? <>{decOut.text}{decOut.warn && <><br /><span style={{ color: 'var(--text-2)' }}>{decOut.warn}</span></>}</> : '결과가 여기에 표시됩니다'}
              </div>
            </div>
          </div>
        </div>
      </div>
    </AppLayout>
  )
}

type Parsed =
  | { kind: 'empty' }
  | { kind: 'error'; message: string }
  | { kind: 'ok'; version: VersionInfo; ok: boolean; old: boolean }

/** 접두 버전 실시간 판정 (서버가 최종 판정) */
function parseInput(raw: string, sig: boolean, versions: VersionInfo[], currentVersion: number): Parsed {
  const text = raw.trim()
  if (!text) return { kind: 'empty' }
  const parts = text.split(':')
  const need = sig ? 2 : 3
  if (parts.length !== need || !/^\d+$/.test(parts[0]) || !parts[parts.length - 1]) {
    return { kind: 'error', message: `형식 오류 — 기대 형식 ${sig ? 'version:signature' : 'version:iv:ciphertext'}` }
  }
  const v = versions.find((x) => x.version === Number(parts[0]))
  if (!v) return { kind: 'error', message: `v${parts[0]} — 존재하지 않는 버전` }
  return { kind: 'ok', version: v, ok: v.state === 'ACTIVE', old: v.state === 'ACTIVE' && v.version !== currentVersion }
}
