import { Maximize, Minimize, Search } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import AppLayout from '@/components/layout/AppLayout'
import { errorMessage, useToast } from '@/components/ui/toast'
import { KeyCreateDialog } from '@/components/keys/KeyCreateDialog'
import { UserFormDialog } from '@/components/users/UserFormDialog'
import { NoticeFormDialog } from '@/components/notices/NoticeFormDialog'
import { PanelBoard } from '@/components/dashboard/PanelBoard'
import { ChainBand, NewMenu, PanelMenu } from '@/components/dashboard/DashMenus'
import { SearchDialog } from '@/components/dashboard/SearchDialog'
import {
  AlgoCard, ExpiringCard, FailuresCard, FeedCard, SignalsCard, StatIntegrity, StatKeys, StatNotices, StatUsers, TrendCard,
} from '@/components/dashboard/DashCards'
import { fetchExpiring, fetchSummary, fetchUsageTrend, type DashboardSummary, type ExpiringItem, type TrendDays, type TrendOp, type UsageTrend } from '@/api/dashboard'
import { fetchChainStatus, listAuditLogs, verifyAuditChain, type AuditLogItem, type AuditVerifyResult } from '@/api/audit'
import { subscribeUiEvents } from '@/lib/events'
import {
  clearTree, defaultTree, loadTree, removeLeaf, saveTree, showPanel, visibleIds, type PanelId, type TreeNode,
} from '@/lib/splitTree'

/* 목업 dashboard.html — 대시보드. 요약 카드 4 · 사용 추이 · 갱신 임박 · 최근 활동 · 보안 신호 · 연산 실패 · 알고리즘 분포.
   모든 카드는 SSE 이벤트(감사 기록 커밋) 수신 시 재조회해 실시간 갱신된다. 배치는 브라우저에 저장(패널 편집·초기화). */

const now = () => new Date().toTimeString().slice(0, 8)

export default function DashboardPage() {
  const toast = useToast()
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [trend, setTrend] = useState<UsageTrend | null>(null)
  const [op, setOp] = useState<TrendOp>('ALL')
  const [days, setDays] = useState<TrendDays>(30)
  const [expiring, setExpiring] = useState<ExpiringItem[]>([])
  const [feed, setFeed] = useState<AuditLogItem[]>([])
  const [fresh, setFresh] = useState<Set<number>>(new Set())
  const [chain, setChain] = useState<AuditVerifyResult | null | 'unavailable'>(null)
  const [verifying, setVerifying] = useState(false)
  const [lastEvt, setLastEvt] = useState<string>('—')
  const [full, setFull] = useState(false)
  const [tree, setTree] = useState<TreeNode>(() => loadTree() ?? defaultTree())
  const [searchOpen, setSearchOpen] = useState(false)
  const [keyOpen, setKeyOpen] = useState(false)
  const [userOpen, setUserOpen] = useState(false)
  const [noticeOpen, setNoticeOpen] = useState(false)
  const feedIds = useRef<Set<number>>(new Set())

  const loadSummary = useCallback(() => fetchSummary().then(setSummary).catch((e) => toast(errorMessage(e), 'error')), [toast])
  const loadExpiring = useCallback(() => fetchExpiring(30).then(setExpiring).catch(() => {}), [])
  const loadChain = useCallback(() => fetchChainStatus().then(setChain).catch(() => setChain('unavailable')), [])
  const loadFeed = useCallback(() => listAuditLogs({ page: 0, size: 10 }).then((p) => {
    const items = p.content
    const known = feedIds.current
    const added = new Set(items.filter((a) => known.size && !known.has(a.id)).map((a) => a.id))
    items.forEach((a) => known.add(a.id))
    setFeed(items)
    if (added.size) { setFresh(added); setTimeout(() => setFresh(new Set()), 1500) }
  }).catch(() => {}), [])

  useEffect(() => { loadSummary(); loadExpiring(); loadChain(); loadFeed() }, [loadSummary, loadExpiring, loadChain, loadFeed])
  useEffect(() => {
    let cancelled = false
    fetchUsageTrend(days, op).then((t) => { if (!cancelled) setTrend(t) }).catch(() => {})
    return () => { cancelled = true }
  }, [days, op])

  // 실시간 — 감사 기록이 커밋될 때마다 {action, target} 이 오므로 300ms 모아서 전부 재조회
  useEffect(() => {
    let t: ReturnType<typeof setTimeout> | null = null
    return subscribeUiEvents((e) => {
      setLastEvt(now())
      if (t) clearTimeout(t)
      t = setTimeout(() => {
        loadSummary(); loadFeed(); loadChain()
        if (e.action.startsWith('KEY')) { loadExpiring(); fetchUsageTrend(days, op).then(setTrend).catch(() => {}) }
      }, 300)
    })
  }, [loadSummary, loadFeed, loadChain, loadExpiring, days, op])

  // 전체화면(관제) 모드 — 사이드바 숨김
  useEffect(() => {
    const onChange = () => {
      const on = !!document.fullscreenElement
      setFull(on)
      document.documentElement.classList.toggle('dash-full', on)
    }
    document.addEventListener('fullscreenchange', onChange)
    return () => { document.removeEventListener('fullscreenchange', onChange); document.documentElement.classList.remove('dash-full') }
  }, [])
  function toggleFull() {
    if (!document.fullscreenElement) document.documentElement.requestFullscreen().catch(() => toast('이 브라우저에서는 전체화면을 사용할 수 없습니다', 'error'))
    else document.exitFullscreen()
  }

  // "/" 단축키 — 입력 중이거나 모달이 열려 있으면 무시
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== '/' || e.ctrlKey || e.metaKey || e.altKey) return
      const t = e.target as HTMLElement
      if (t.closest('input,textarea,select,[contenteditable]') || document.querySelector('[data-state="open"][role="dialog"]')) return
      e.preventDefault()
      setSearchOpen(true)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  async function verify() {
    setVerifying(true)
    try {
      const { data, message } = await verifyAuditChain()
      setChain(data)
      toast(message ?? (data.healthy ? '해시 체인 검증을 통과했습니다.' : `체인 위반 ${data.violations.length}건이 감지되었습니다.`), data.healthy ? 'ok' : 'error')
    } catch (e) {
      toast(errorMessage(e), 'error')
    } finally {
      setVerifying(false)
    }
  }

  function changeTree(t: TreeNode) { setTree(t); saveTree(t) }
  const visible = useMemo(() => new Set(visibleIds(tree)), [tree])
  function togglePanel(id: PanelId) { changeTree(visible.has(id) ? removeLeaf(tree, id) : showPanel(tree, id)) }
  function resetLayout() { clearTree(); setTree(defaultTree()); toast('기본 배치로 되돌렸습니다.') }

  const cards = {
    keys: <StatKeys s={summary?.keys ?? null} />,
    users: <StatUsers s={summary?.users ?? null} />,
    notices: <StatNotices s={summary?.notices ?? null} />,
    integrity: <StatIntegrity s={summary?.integrity ?? null} />,
    trend: <TrendCard trend={trend} op={op} days={days} onOp={setOp} onDays={setDays} />,
    expiring: <ExpiringCard items={expiring} />,
    feed: <FeedCard items={feed} fresh={fresh} />,
    signals: <SignalsCard signals={summary?.signals ?? []} />,
    fails: <FailuresCard failures={summary?.failures ?? []} />,
    algos: <AlgoCard algorithms={summary?.algorithms ?? []} />,
  }
  const cardClass = {
    keys: 'stat', users: 'stat', notices: 'stat',
    integrity: `stat ${summary?.integrity.total ? 'warn-ring' : ''}`,
  }

  return (
    <AppLayout>
      <div className="dash">
        <div className="page-h">
          <div className="ttl">
            <h2>대시보드</h2>
            <span className="live"><i />실시간 · 마지막 이벤트 <span className="mono">{lastEvt}</span></span>
          </div>
          <div className="acts">
            <button type="button" className="sbox" onClick={() => setSearchOpen(true)} aria-label="검색">
              <Search size={14} /><kbd>/</kbd><span>를 눌러 검색하세요</span>
            </button>
            <button type="button" className="icon-btn" data-tip={full ? '전체화면 종료' : '전체화면'} aria-label={full ? '전체화면 종료' : '전체화면'} onClick={toggleFull}>
              {full ? <Minimize size={16} /> : <Maximize size={16} />}
            </button>
          </div>
        </div>

        <div className="dash-top">
          <ChainBand chain={chain} verifying={verifying} onVerify={verify} />
          <div className="quick">
            <NewMenu onKey={() => setKeyOpen(true)} onUser={() => setUserOpen(true)} onNotice={() => setNoticeOpen(true)} />
            <PanelMenu visible={visible} onToggle={togglePanel} onReset={resetLayout} />
          </div>
        </div>

        <PanelBoard tree={tree} onChange={changeTree} cards={cards} cardClass={cardClass} />
      </div>

      <SearchDialog open={searchOpen} onOpenChange={setSearchOpen} />
      <KeyCreateDialog open={keyOpen} onOpenChange={setKeyOpen} onCreated={loadSummary} />
      <UserFormDialog edit={null} open={userOpen} onClose={() => setUserOpen(false)} onDone={loadSummary} />
      <NoticeFormDialog edit={null} open={noticeOpen} onClose={() => setNoticeOpen(false)} onDone={loadSummary} />
    </AppLayout>
  )
}
