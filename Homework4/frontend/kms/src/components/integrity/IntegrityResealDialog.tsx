import { ReasonDialog } from '@/components/ui/reason-dialog'

/* 무결성 재해시 확인 모달 — 키·사용자 공용. 위반에서 정상으로 가는 유일한 경로라 사유를 받는다(공용 ReasonDialog).
   ADMIN 한정, 서버가 현재 값으로 해시를 다시 봉인하고 *_INTEGRITY_RESEALED 감사 기록을 남긴다 */
export function IntegrityResealDialog({ onClose, run }: {
  onClose: () => void
  run: (reason: string) => Promise<{ message: string | null }>
}) {
  return (
    <ReasonDialog title="현재 값으로 재해시" confirmLabel="재해시" placeholder="예: 변조 원인 확인 후 재봉인"
      doneMessage="현재 값으로 재해시되었습니다." onClose={onClose} run={run} />
  )
}
