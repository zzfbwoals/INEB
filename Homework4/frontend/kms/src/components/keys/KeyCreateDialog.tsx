import { useState, type FormEvent } from 'react'
import { createKey, type KeyAlgorithm, type KeyMode } from '@/api/keys'
import { ALGOS, ALGO_GROUPS, PURPOSE_KO, ROT_DEFAULT, ROT_MAX, ROT_MIN } from '@/lib/keyRules'
import { fromDateTimeLocal } from '@/lib/format'
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
      toast('키가 생성되었습니다')
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
      <DialogContent title="키 등록" wide>
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
                  {autoRotate && !rotValid && <span style={{ color: 'var(--red)' }}>1~730 범위여야 합니다</span>}
                </span>
              </div>
            </div>
            <div className="field">
              <label>활성일</label>
              <Input className="mono" type="datetime-local" value={activationDate} onChange={(e) => setActivationDate(e.target.value)} />
            </div>
            <div className="field">
              <label>설명</label>
              <Input placeholder="용도·연동 시스템 등 (선택)" value={description} onChange={(e) => setDescription(e.target.value)} />
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>취소</Button>
            <Button type="submit" disabled={pending}>{pending ? '생성 중…' : '생성'}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
