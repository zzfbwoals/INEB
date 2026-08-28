import { useState, type FormEvent } from 'react'
import { createKey, type KeyAlgorithm, type KeyMode } from '@/api/keys'
import { ALGOS, ALGO_GROUPS, PURPOSE_HELP, PURPOSE_KO, ROT_DEFAULT, ROT_MAX, ROT_MIN } from '@/lib/keyRules'
import { fromDateTimeLocal, isFuture, plusDays } from '@/lib/format'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogBody, DialogContent, DialogFooter } from '@/components/ui/dialog'
import { errorMessage, useToast } from '@/components/ui/toast'

/* 목업 keys.html "KMS 키 등록" 모달 — 알고리즘 → 사이즈·모드·용도 연동, 갱신 주기·활성일 미리보기 */
export function KeyCreateDialog({ open, onOpenChange, onCreated }: { open: boolean; onOpenChange: (o: boolean) => void; onCreated: () => void }) {
  const toast = useToast()
  const [keyName, setKeyName] = useState('')
  const [algorithm, setAlgorithm] = useState<KeyAlgorithm | ''>('')
  const [keySize, setKeySize] = useState<number>(0)
  const [mode, setMode] = useState<KeyMode | ''>('')
  const [autoRotate, setAutoRotate] = useState(true)
  const [rotationDays, setRotationDays] = useState<number>(ROT_DEFAULT)
  const [activationDate, setActivationDate] = useState('')
  const [description, setDescription] = useState('')
  const [pending, setPending] = useState(false)

  const algo = algorithm ? ALGOS[algorithm] : null
  const purpose = algo?.purpose ?? null
  const future = isFuture(activationDate)
  const rotValid = rotationDays >= ROT_MIN && rotationDays <= ROT_MAX

  function reset() {
    setKeyName(''); setAlgorithm(''); setKeySize(0); setMode(''); setAutoRotate(true)
    setRotationDays(ROT_DEFAULT); setActivationDate(''); setDescription('')
  }

  function handleAlgorithm(value: string) {
    const a = value as KeyAlgorithm | ''
    setAlgorithm(a)
    if (!a) { setKeySize(0); setMode(''); return }
    const rule = ALGOS[a]
    setKeySize(rule.sizes[rule.sizes.length - 1])
    setMode(rule.modes.length ? rule.modes[0] : '')
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const name = keyName.trim()
    if (!name) { toast('키명을 입력해주세요', 'error'); return }
    if (!algorithm || !algo || !purpose) { toast('알고리즘을 선택해주세요', 'error'); return }
    if (autoRotate && !rotValid) { toast('갱신 주기는 1~730일이어야 합니다', 'error'); return }
    setPending(true)
    try {
      await createKey({
        keyName: name,
        algorithm,
        keySize,
        mode: algo.modes.length ? (mode as KeyMode) : null,
        purpose,
        autoRotate,
        rotationPeriodDays: autoRotate ? rotationDays : null,
        activationDate: fromDateTimeLocal(activationDate),
        description: description.trim() || null,
      })
      toast(`'${name}' 생성 · 마스터키 래핑 저장 (v1 ${future ? 'PRE_ACTIVE · ' + activationDate.slice(0, 10) + ' 활성 예정' : 'ACTIVE'}${autoRotate ? ' · ' + rotationDays + '일 자동 갱신' : ''})`)
      onOpenChange(false)
      reset()
      onCreated()
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { onOpenChange(o); if (!o) reset() }}>
      <DialogContent title="KMS 키 등록" wide>
        <form onSubmit={handleSubmit}>
          <DialogBody>
            <div className="field">
              <label>키명 <em>*</em></label>
              <Input placeholder="예: PAY-GW-AES256" value={keyName} onChange={(e) => setKeyName(e.target.value)} autoFocus />
            </div>
            <div className="fgrid">
              <div className="field">
                <label>알고리즘 <em>*</em></label>
                <select className="input" value={algorithm} onChange={(e) => handleAlgorithm(e.target.value)}>
                  <option value="">선택하세요</option>
                  {ALGO_GROUPS.map((g) => (
                    <optgroup key={g.label} label={g.label}>
                      {g.items.map((a) => <option key={a} value={a}>{a}</option>)}
                    </optgroup>
                  ))}
                </select>
              </div>
              <div className="field">
                <label>사이즈 (bit) <em>*</em></label>
                <select className="input" value={keySize} disabled={!algo} onChange={(e) => setKeySize(Number(e.target.value))}>
                  {!algo && <option value={0}>—</option>}
                  {algo?.sizes.map((s) => <option key={s} value={s}>{algo.sizeLabel ? algo.sizeLabel(s) : s}</option>)}
                </select>
              </div>
            </div>
            <div className="fgrid">
              <div className="field">
                <label>모드</label>
                <select className="input" value={mode} disabled={!algo || algo.modes.length === 0} onChange={(e) => setMode(e.target.value as KeyMode)}>
                  {!algo && <option value="">—</option>}
                  {algo && algo.modes.length === 0 && <option value="">해당 없음 ({algo.kind === 'HMAC' ? 'HMAC' : '비대칭키'})</option>}
                  {algo?.modes.map((m) => <option key={m} value={m}>{m}{m === 'ECB' ? ' (비권장)' : ''}</option>)}
                </select>
              </div>
              <div className="field">
                <label>용도 <em>*</em></label>
                <select className="input" value={purpose ?? ''} disabled>
                  {!purpose && <option value="">알고리즘 선택 시 결정</option>}
                  {purpose && <option value={purpose}>{PURPOSE_KO[purpose]}</option>}
                </select>
                <div className="help">{purpose ? PURPOSE_HELP[purpose] : '알고리즘 선택 시 가능한 용도로 자동 설정됩니다'}</div>
              </div>
            </div>
            <div className="field">
              <label>갱신 주기 (회전)</label>
              <div className="rot-row">
                <label className="sw">
                  <input type="checkbox" checked={autoRotate} onChange={(e) => setAutoRotate(e.target.checked)} />자동 갱신
                </label>
                <input className="input mono" type="number" min={ROT_MIN} max={ROT_MAX} value={rotationDays} disabled={!autoRotate}
                  onChange={(e) => setRotationDays(Number(e.target.value))} />
                <span style={{ fontSize: 13, color: 'var(--text-2)' }}>일</span>
                <span className="help">
                  {!autoRotate ? '수동 갱신만 — 주기적 갱신은 관리자 책임'
                    : !rotValid ? <span style={{ color: 'var(--red)' }}>1~730 범위여야 합니다</span>
                      : `다음 갱신 ${plusDays(rotationDays)}`}
                </span>
              </div>
              <div className="help">1 ~ 730일 (기본 90일). 주기 도래 시 새 버전이 자동 생성되어 최신 버전이 되며, 이전 버전은 복호화·검증 전용으로 유지됩니다</div>
            </div>
            <div className="field">
              <label>활성일</label>
              <Input className="mono" type="datetime-local" value={activationDate} onChange={(e) => setActivationDate(e.target.value)} />
              <div className="help">비우거나 과거 → 즉시 <b>ACTIVE</b> / 미래 → <b>PRE_ACTIVE</b>로 등록되어 도래 시 자동 활성</div>
            </div>
            <div className="field">
              <label>설명</label>
              <Input placeholder="용도·연동 시스템 등 (선택)" value={description} onChange={(e) => setDescription(e.target.value)} />
            </div>
            <div className="result-box" style={{ minHeight: 0 }}>
              SecureRandom으로 키 재료 생성 → 마스터키 AES-256-GCM 래핑 → Base64 저장 (key_material v1)<br />
              key_uid(UUID) 자동 부여 · 초기 상태{' '}
              {future ? <><b style={{ color: 'var(--blue)' }}>PRE_ACTIVE</b> ({activationDate.replace('T', ' ')} 활성 예정)</>
                : <><b style={{ color: 'var(--green)' }}>ACTIVE</b> (즉시 활성)</>} · integrity_hash 계산<br />
              갱신: {autoRotate ? <><b>{rotationDays}일</b>마다 자동 (SYSTEM)</> : '수동'}
              {algo?.kind === 'ASYMMETRIC' && <><br /><span style={{ color: 'var(--text-3)' }}>비대칭키 — 개인키만 래핑 저장, 공개키는 상세에서 조회 가능</span></>}
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>취소</Button>
            <Button type="submit" disabled={pending}>{pending ? '생성 중…' : '생성 및 래핑 저장'}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
