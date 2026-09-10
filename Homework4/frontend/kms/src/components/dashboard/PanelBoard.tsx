import { GripVertical, X } from 'lucide-react'
import { Fragment, createContext, useContext, useRef, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react'
import {
  clone, insertLeaf, removeLeaf, visibleIds,
  type ContainerNode, type PanelId, type TreeNode, type Zone,
} from '@/lib/splitTree'

/* 패널 보드 — 목업 dashboard.html 의 분할 트리 보드.
   · 카드는 트리의 리프 자리에 flex 비율(size)로 놓이고, 형제 사이 12px 분할선(.split)을 끌면 양옆 두 카드만 비율을 나눈다
   · 손잡이(⋮⋮)를 끌면 복제본(고스트)이 따라오고, 놓을 카드의 가장자리에 파란 막대(드롭 표시)가 뜬다. 놓으면 트리를 갱신
   · 드래그·크기 조절 중에는 DOM 스타일을 직접 만지고, 끝났을 때만 onChange(새 트리)로 커밋한다 */

interface BoardApi {
  startDrag: (id: PanelId, e: ReactPointerEvent) => void
  close: (id: PanelId) => void
}
const BoardCtx = createContext<BoardApi | null>(null)

const MIN_W = 160
const MIN_H = 80
const clamp = (v: number, a: number, b: number) => Math.min(Math.max(v, a), b)

function getNode(root: TreeNode, path: number[]): ContainerNode {
  let n: TreeNode = root
  for (const i of path) n = (n as ContainerNode).children[i]
  return n as ContainerNode
}

export function PanelBoard({ tree, onChange, cards, cardClass }: {
  tree: TreeNode
  onChange: (t: TreeNode) => void
  cards: Record<PanelId, ReactNode>
  /** 카드별 추가 클래스 (요약 카드 .stat, 위반 시 .warn-ring 등) */
  cardClass?: Partial<Record<PanelId, string>>
}) {
  const boardRef = useRef<HTMLDivElement>(null)
  const indRef = useRef<HTMLDivElement>(null)
  const cardEls = useRef(new Map<PanelId, HTMLDivElement>())

  // 분할선 — 양옆 형제(a,b)의 size 합을 유지하며 픽셀 비율로 재분배
  function splitDown(e: ReactPointerEvent<HTMLDivElement>, path: number[], i: number, horiz: boolean) {
    if (e.button !== 0) return
    e.preventDefault()
    const sp = e.currentTarget
    const aEl = sp.previousElementSibling as HTMLElement
    const bEl = sp.nextElementSibling as HTMLElement
    const n = getNode(tree, path)
    const a = n.children[i - 1]
    const b = n.children[i]
    const ra = aEl.getBoundingClientRect()
    const rb = bEl.getBoundingClientRect()
    const aPx = horiz ? ra.width : ra.height
    const bPx = horiz ? rb.width : rb.height
    const total = aPx + bPx
    const sum = a.size + b.size
    const min = Math.min(horiz ? MIN_W : MIN_H, total / 2)
    const s0 = horiz ? e.clientX : e.clientY
    let aSize = a.size
    sp.classList.add('on')
    document.body.style.cursor = horiz ? 'col-resize' : 'row-resize'
    const mv = (ev: PointerEvent) => {
      const na = clamp(aPx + ((horiz ? ev.clientX : ev.clientY) - s0), min, total - min)
      aSize = (sum * na) / total
      aEl.style.flexGrow = String(aSize)
      bEl.style.flexGrow = String(sum - aSize)
    }
    const up = () => {
      window.removeEventListener('pointermove', mv)
      window.removeEventListener('pointerup', up)
      sp.classList.remove('on')
      document.body.style.cursor = ''
      const t = clone(tree)
      const nn = getNode(t, path)
      nn.children[i - 1].size = aSize
      nn.children[i].size = sum - aSize
      onChange(t)
    }
    window.addEventListener('pointermove', mv)
    window.addEventListener('pointerup', up)
  }

  // 놓을 자리 판정 — 커서에 가장 가까운(자기 제외) 카드와 그 카드 안에서 가장 가까운 가장자리
  function hitTest(x: number, y: number, selfId: PanelId): { id: PanelId; r: DOMRect; zone: Zone } | null {
    const b = boardRef.current!.getBoundingClientRect()
    if (x < b.left || x > b.right || y < b.top || y > b.bottom) return null
    let best: { id: PanelId; r: DOMRect } | null = null
    let bd = Infinity
    visibleIds(tree).filter((i) => i !== selfId).forEach((i) => {
      const el = cardEls.current.get(i)
      if (!el) return
      const r = el.getBoundingClientRect()
      const d = Math.hypot(Math.max(r.left - x, 0, x - r.right), Math.max(r.top - y, 0, y - r.bottom))
      if (d < bd) { bd = d; best = { id: i, r } }
    })
    if (!best) return null
    const { id, r } = best as { id: PanelId; r: DOMRect }
    const dl = (x - r.left) / r.width, dr = 1 - dl, dt = (y - r.top) / r.height, db = 1 - dt
    const m = Math.min(dl, dr, dt, db)
    return { id, r, zone: m === dl ? 'left' : m === dr ? 'right' : m === dt ? 'top' : 'bottom' }
  }

  function showInd(t: { r: DOMRect; zone: Zone } | null) {
    const ind = indRef.current!
    if (!t) { ind.style.display = 'none'; return }
    const b = boardRef.current!.getBoundingClientRect()
    const r = t.r
    const T = 4
    ind.style.display = 'block'
    if (t.zone === 'left' || t.zone === 'right') {
      ind.style.top = `${r.top - b.top}px`; ind.style.height = `${r.height}px`; ind.style.width = `${T}px`
      ind.style.left = `${(t.zone === 'left' ? r.left - 8 : r.right + 4) - b.left}px`
    } else {
      ind.style.left = `${r.left - b.left}px`; ind.style.width = `${r.width}px`; ind.style.height = `${T}px`
      ind.style.top = `${(t.zone === 'top' ? r.top - 8 : r.bottom + 4) - b.top}px`
    }
  }

  // 손잡이 드래그 — 4px 이상 움직이면 고스트가 커서를 따라가고 원본은 흐려진다. 놓으면 트리에서 빼서 목표 자리에 끼운다
  const api: BoardApi = {
    startDrag(id, e) {
      if (e.button !== 0) return
      e.preventDefault()
      const c = cardEls.current.get(id)
      if (!c) return
      const sx = e.clientX, sy = e.clientY
      const r0 = c.getBoundingClientRect()
      const offX = sx - r0.left, offY = sy - r0.top
      let ghost: HTMLElement | null = null
      let target: { id: PanelId; r: DOMRect; zone: Zone } | null = null
      const mv = (ev: PointerEvent) => {
        if (!ghost) {
          if (Math.hypot(ev.clientX - sx, ev.clientY - sy) < 4) return
          ghost = c.cloneNode(true) as HTMLElement
          ghost.classList.add('ghost')
          ghost.style.width = `${r0.width}px`
          ghost.style.height = `${r0.height}px`
          document.body.appendChild(ghost)
          c.classList.add('dragging')
          document.body.style.cursor = 'grabbing'
        }
        ghost.style.left = `${ev.clientX - offX}px`
        ghost.style.top = `${ev.clientY - offY}px`
        target = hitTest(ev.clientX, ev.clientY, id)
        showInd(target)
      }
      const up = () => {
        window.removeEventListener('pointermove', mv)
        window.removeEventListener('pointerup', up)
        if (!ghost) return
        ghost.remove()
        c.classList.remove('dragging')
        document.body.style.cursor = ''
        showInd(null)
        if (target) onChange(insertLeaf(removeLeaf(tree, id), id, target.id, target.zone))
      }
      window.addEventListener('pointermove', mv)
      window.addEventListener('pointerup', up)
    },
    close(id) {
      onChange(removeLeaf(tree, id))
    },
  }

  function build(n: TreeNode, path: number[]): ReactNode {
    if (n.type === 'panel') {
      return (
        <div
          key={n.id}
          className={`card ${cardClass?.[n.id] ?? ''}`}
          data-card={n.id}
          style={{ flex: `${n.size} 1 0` }}
          ref={(el) => { if (el) cardEls.current.set(n.id, el); else cardEls.current.delete(n.id) }}
        >
          {cards[n.id]}
        </div>
      )
    }
    const horiz = n.type === 'row'
    return (
      <div key={path.join('.') || 'root'} className={`node ${n.type}`} style={{ flex: path.length ? `${n.size} 1 0` : '1 1 0' }}>
        {n.children.map((c, i) => (
          <Fragment key={c.type === 'panel' ? c.id : `c${i}`}>
            {i > 0 && (
              <div
                className={`split ${horiz ? 'v' : 'h'}`}
                onPointerDown={(e) => splitDown(e, path, i, horiz)}
              />
            )}
            {build(c, [...path, i])}
          </Fragment>
        ))}
      </div>
    )
  }

  return (
    <BoardCtx.Provider value={api}>
      <div className="board" ref={boardRef}>
        {build(tree, [])}
        <div ref={indRef} className="drop-ind" style={{ display: 'none' }} />
      </div>
    </BoardCtx.Provider>
  )
}

/** 카드 공통 헤더 — 손잡이(이동) · 제목 · 제목 옆 링크(more) · 우측 요소(right) · X 닫기 */
export function CardHead({ id, title, more, right }: { id: PanelId; title: ReactNode; more?: ReactNode; right?: ReactNode }) {
  const api = useContext(BoardCtx)!
  return (
    <div className="card-h">
      <span className="grip" aria-label="이동" onPointerDown={(e) => api.startDrag(id, e)}>
        <GripVertical size={14} />
      </span>
      <h3>{title}</h3>
      {more}
      {right && <div className="hr">{right}</div>}
      <button type="button" className="icon-btn sm cx" data-tip="닫기" aria-label="닫기" onClick={() => api.close(id)}>
        <X size={14} />
      </button>
    </div>
  )
}
