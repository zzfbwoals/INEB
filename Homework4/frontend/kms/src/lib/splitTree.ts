/* 대시보드 패널 보드의 분할 트리 (목업 dashboard.html 의 트리 로직을 그대로 옮긴 순수 함수들).
   트리: row/col 컨테이너 + panel 리프. size 는 형제 사이의 flex 비율이며 형제 합은 항상 100 으로 맞춘다
   (flex-grow 합이 1 미만이면 남는 공간을 그 비율만큼만 써서 행 오른쪽 끝이 비기 때문).
   · 이동: 손잡이로 끌어 다른 카드의 가장자리에 놓으면 같은 방향 컨테이너면 형제로 끼어들고, 아니면 그 카드와 둘이 새 컨테이너로 묶인다
   · 닫기: 리프 제거 후 자식 하나뿐인 컨테이너는 걷어내고 같은 방향 중첩은 평탄화
   · 다시 표시: 기본 배치에서 같은 행에 있던 이웃 중 보이는 카드 옆(원래 순서·기본 비율)에 끼운다
   배치는 브라우저(localStorage kms.dash.layout.v2)에 저장한다 — 단일 관리자 환경이라 서버 저장은 두지 않는다. */

export type PanelId =
  | 'keys' | 'users' | 'notices' | 'integrity'
  | 'trend' | 'expiring' | 'algos'
  | 'feed' | 'signals' | 'fails'

export type Zone = 'left' | 'right' | 'top' | 'bottom'

export interface PanelNode { type: 'panel'; id: PanelId; size: number }
export interface ContainerNode { type: 'row' | 'col'; size: number; k?: string; children: TreeNode[] }
export type TreeNode = PanelNode | ContainerNode

export const LS_KEY = 'kms.dash.layout.v2'

export const PANEL_TITLES: Record<PanelId, string> = {
  keys: '전체 KMS 키', users: '서비스 사용자', notices: '공지사항', integrity: '무결성 위반',
  trend: '키 사용 추이', expiring: '갱신 임박 · 예약 활성', algos: '알고리즘 분포',
  feed: '최근 활동', signals: '보안 신호', fails: '연산 실패',
}

export const PANEL_GROUPS: [string, PanelId[]][] = [
  ['요약', ['keys', 'users', 'notices', 'integrity']],
  ['분석', ['trend', 'expiring', 'algos']],
  ['활동', ['feed', 'signals', 'fails']],
]

const DEFAULT_ROW: Record<PanelId, string> = {
  keys: 'A', users: 'A', notices: 'A', integrity: 'A',
  trend: 'B', expiring: 'B', feed: 'C', signals: 'C', fails: 'C', algos: 'C',
}

/* 기본 배치 — 목업의 그리드(요약 4열 / 1.65:1 / 1.6:1:1.2:1)를 측정한 비율. 창 크기와 무관하게 같은 모양 */
export function defaultTree(): ContainerNode {
  const panel = (id: PanelId, size: number): PanelNode => ({ type: 'panel', id, size })
  return {
    type: 'col', size: 100, children: [
      { type: 'row', k: 'A', size: 25, children: [panel('keys', 25), panel('users', 25), panel('notices', 25), panel('integrity', 25)] },
      { type: 'row', k: 'B', size: 40, children: [panel('trend', 62), panel('expiring', 38)] },
      { type: 'row', k: 'C', size: 35, children: [panel('feed', 33), panel('signals', 21), panel('fails', 25), panel('algos', 21)] },
    ],
  }
}

export const clone = <T,>(t: T): T => JSON.parse(JSON.stringify(t)) as T

export function visibleIds(n: TreeNode, out: PanelId[] = []): PanelId[] {
  if (n.type === 'panel') out.push(n.id)
  else n.children.forEach((c) => visibleIds(c, out))
  return out
}

export function findParent(n: TreeNode, id: PanelId): ContainerNode | null {
  if (n.type === 'panel') return null
  for (const c of n.children) {
    if (c.type === 'panel' && c.id === id) return n
    const r = findParent(c, id)
    if (r) return r
  }
  return null
}

/** 자식 하나뿐인 컨테이너 제거 · 같은 방향 중첩 평탄화 · 형제 합 100 정규화. 새 트리를 반환한다 */
export function normalize(root: TreeNode): TreeNode {
  const walk = (n: TreeNode, isRoot: boolean): TreeNode => {
    if (n.type === 'panel') return n
    const kids = n.children.map((c) => walk(c, false)).filter((c) => c.type === 'panel' || c.children.length)
    const flat: TreeNode[] = []
    kids.forEach((c) => {
      if (c.type !== 'panel' && c.type === n.type) {
        const s = c.children.reduce((a, x) => a + x.size, 0) || 1
        c.children.forEach((x) => flat.push({ ...x, size: (c.size * x.size) / s }))
      } else flat.push(c)
    })
    if (flat.length === 1 && !isRoot) return { ...flat[0], size: n.size }
    const sum = flat.reduce((a, x) => a + x.size, 0) || 1
    return { ...n, children: flat.map((x) => ({ ...x, size: (x.size / sum) * 100 })) }
  }
  let t = walk(root, true)
  if (t.type !== 'panel' && t.children.length === 1 && t.children[0].type !== 'panel') t = { ...t.children[0], size: 100 }
  return t
}

export function removeLeaf(root: TreeNode, id: PanelId): TreeNode {
  const t = clone(root)
  const p = findParent(t, id)
  if (!p) return t
  p.children = p.children.filter((c) => !(c.type === 'panel' && c.id === id))
  return normalize(t)
}

/** sizes = [leaf, target] 비율을 주면 그대로, 없으면 형제 평균 / 1:1 */
export function insertLeaf(root: TreeNode, id: PanelId, targetId: PanelId, zone: Zone, sizes?: [number, number]): TreeNode {
  const t = clone(root)
  const p = findParent(t, targetId)
  if (!p) return t
  const i = p.children.findIndex((c) => c.type === 'panel' && c.id === targetId)
  const target = p.children[i]
  const horiz = zone === 'left' || zone === 'right'
  const after = zone === 'right' || zone === 'bottom'
  const want = horiz ? 'row' : 'col'
  if (p.type === want) {
    const size = sizes ? (target.size * sizes[0]) / sizes[1] : p.children.reduce((a, x) => a + x.size, 0) / p.children.length
    p.children.splice(after ? i + 1 : i, 0, { type: 'panel', id, size })
  } else {
    const leaf: PanelNode = { type: 'panel', id, size: sizes ? sizes[0] : 1 }
    const tgt = { ...target, size: sizes ? sizes[1] : 1 }
    p.children[i] = { type: want, size: target.size, children: after ? [tgt, leaf] : [leaf, tgt] }
  }
  return normalize(t)
}

/** 다시 표시 — 기본 행의 이웃 옆에 끼우고, 이웃이 모두 숨겨졌으면 원래 행 자리에 새 행 */
export function showPanel(root: TreeNode, id: PanelId): TreeNode {
  const d = defaultTree()
  const k = DEFAULT_ROW[id]
  const drow = d.children.find((r) => r.type !== 'panel' && r.k === k) as ContainerNode
  const ids = drow.children.map((c) => (c as PanelNode).id)
  const dsize = (x: PanelId) => (drow.children.find((c) => (c as PanelNode).id === x) as PanelNode).size
  const vis = new Set(visibleIds(root))
  const i = ids.indexOf(id)
  let sib: PanelId | null = null
  let zone: Zone = 'right'
  for (let j = i - 1; j >= 0 && !sib; j--) if (vis.has(ids[j])) sib = ids[j]
  if (!sib) {
    zone = 'left'
    for (let j = i + 1; j < ids.length && !sib; j++) if (vis.has(ids[j])) sib = ids[j]
  }
  if (sib) return insertLeaf(root, id, sib, zone, [dsize(id), dsize(sib)])
  let t = clone(root)
  if (t.type !== 'col') t = { type: 'col', size: 100, children: [{ ...t, size: 100 }] }
  const nr: ContainerNode = { type: 'row', k, size: drow.size, children: [{ type: 'panel', id, size: 100 }] }
  const order = (n: TreeNode) => (n.type !== 'panel' && n.k) || visibleIds(n).map((x) => DEFAULT_ROW[x]).sort()[0] || 'Z'
  const idx = t.children.findIndex((n) => order(n) > k)
  t.children.splice(idx < 0 ? t.children.length : idx, 0, nr)
  return normalize(t)
}

export function loadTree(): TreeNode | null {
  try {
    const v = JSON.parse(localStorage.getItem(LS_KEY) ?? 'null') as { v: number; tree: TreeNode } | null
    if (v && v.v === 2 && v.tree) {
      const known = new Set(Object.keys(PANEL_TITLES))
      const prune = (n: TreeNode) => {
        if (n.type === 'panel') return
        n.children = n.children.filter((c) => c.type !== 'panel' || known.has(c.id))
        n.children.forEach(prune)
      }
      prune(v.tree)
      return normalize(v.tree)
    }
  } catch {
    // 손상된 저장본은 무시
  }
  return null
}

export function saveTree(t: TreeNode): void {
  try {
    localStorage.setItem(LS_KEY, JSON.stringify({ v: 2, tree: t }))
  } catch {
    // 저장 불가 환경은 무시
  }
}

export function clearTree(): void {
  try {
    localStorage.removeItem(LS_KEY)
  } catch {
    // 무시
  }
}
