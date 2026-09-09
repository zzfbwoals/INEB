import { Check, ChevronUp, FlaskConical, KeyRound, LayoutDashboard, LogOut, MessageSquareText, Monitor, Moon, PanelLeft, ShieldCheck, Sun, Users } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router'
import { fetchMe, logout, type Me } from '@/api/auth'
import { clearToken } from '@/lib/auth'
import { setThemeMode, useThemeMode, type ThemeMode } from '@/lib/theme'
import { LogoFull, LogoMark } from '@/components/brand'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

/* 아이콘은 lucide-react — 목업 shell.js 의 인라인 SVG 와 같은 선형(24px·stroke 2) 세트 */
const icons = {
  dash: <LayoutDashboard size={16} />,
  key: <KeyRound size={16} />,
  test: <FlaskConical size={16} />,
  user: <Users size={16} />,
  notice: <MessageSquareText size={16} />,
  audit: <ShieldCheck size={16} />,
  sun: <Sun size={14} />,
  moon: <Moon size={14} />,
  sys: <Monitor size={14} />,
  out: <LogOut size={14} />,
  chk: <Check className="chk" size={13} strokeWidth={2.6} />,
} as const

/* 사이드바 메뉴 — to 가 있으면 라우팅, 없으면 미구현(추후 주차) 표시만 */
const NAV: { section: string | null; items: { key: string; label: string; icon: ReactNode; to?: string }[] }[] = [
  { section: null, items: [{ key: 'dashboard', label: '대시보드', icon: icons.dash, to: '/' }] },
  {
    section: '키 관리',
    items: [
      { key: 'keys', label: '키 목록', icon: icons.key, to: '/keys' },
      { key: 'test', label: '동작 테스트', icon: icons.test, to: '/keys/test' },
    ],
  },
  {
    section: '운영 관리',
    items: [
      { key: 'users', label: '사용자 관리', icon: icons.user, to: '/users' },
      { key: 'notices', label: '공지사항', icon: icons.notice, to: '/notices' },
      { key: 'audit', label: '감사 로그', icon: icons.audit, to: '/audit' },
    ],
  },
]

const THEME_ITEMS: { mode: ThemeMode; label: string; icon: ReactNode }[] = [
  { mode: 'light', label: '라이트 모드', icon: icons.sun },
  { mode: 'dark', label: '다크 모드', icon: icons.moon },
  { mode: 'system', label: '시스템 설정', icon: icons.sys },
]

export default function AppLayout({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const themeMode = useThemeMode()
  const [me, setMe] = useState<Me | null>(null)
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem('kms.side') === 'collapsed'
    } catch {
      return false
    }
  })

  useEffect(() => {
    fetchMe()
      .then(setMe)
      .catch(() => {
        // 401은 axios 인터셉터가 /login으로 보냄
      })
  }, [])

  useEffect(() => {
    document.documentElement.classList.toggle('collapsed', collapsed)
    return () => document.documentElement.classList.remove('collapsed')
  }, [collapsed])

  function toggleSide() {
    setCollapsed((prev) => {
      const next = !prev
      try {
        localStorage.setItem('kms.side', next ? 'collapsed' : 'open')
      } catch {
        // 무시
      }
      return next
    })
  }

  async function handleLogout() {
    await logout()
    clearToken()
    navigate('/login', { replace: true })
  }

  return (
    <div className="shell">
      <aside className="side">
        <div className="brand">
          <LogoFull className="full" />
          <LogoMark className="mark" />
          {/* 확장 시 우측 축소 버튼 / 축소 시 마크 호버로 나타나는 확대 버튼 */}
          <button type="button" className="tgl side-tgl" onClick={toggleSide} title="사이드바 열기/닫기" aria-label="사이드바 열기/닫기">
            <PanelLeft size={17} />
          </button>
        </div>

        {NAV.map((group) => (
          <div key={group.section ?? 'root'} className="contents">
            {group.section && <div className="nav-sec">{group.section}</div>}
            {group.items.map((item) =>
              item.to ? (
                <NavLink
                  key={item.key}
                  to={item.to}
                  end
                  className={({ isActive }) => (isActive ? 'nav-it on' : 'nav-it')}
                  data-label={item.label}
                >
                  {item.icon}
                  <span>{item.label}</span>
                </NavLink>
              ) : (
                <button key={item.key} type="button" className="nav-it" data-label={item.label} title="추후 제공 예정">
                  {item.icon}
                  <span>{item.label}</span>
                </button>
              ),
            )}
          </div>
        ))}

        <div className="side-profile">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button type="button" className="sp-btn" aria-label="프로필 메뉴">
                <span className="avatar-btn">{me?.name?.charAt(0) ?? '?'}</span>
                <span className="sp-who">
                  <b>{me?.name ?? '···'}</b>
                  <span>{me?.role ?? ''}</span>
                </span>
                <ChevronUp className="sp-chev" size={13} strokeWidth={2.2} />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent side="top" align="start">
              <div className="mm-head">
                <b>{me?.name ?? '···'}</b>
                <span>
                  {me?.role ?? ''} · {me?.loginId ?? ''}
                </span>
              </div>
              <DropdownMenuLabel>테마 설정</DropdownMenuLabel>
              {THEME_ITEMS.map((item) => (
                <DropdownMenuItem
                  key={item.mode}
                  className={themeMode === item.mode ? 'on' : undefined}
                  onSelect={(e) => {
                    e.preventDefault() // 메뉴를 닫지 않고 체크 표시 갱신
                    setThemeMode(item.mode)
                  }}
                >
                  {item.icon}
                  {item.label}
                  {icons.chk}
                </DropdownMenuItem>
              ))}
              <DropdownMenuSeparator />
              <DropdownMenuItem className="danger" onSelect={handleLogout}>
                {icons.out}
                로그아웃
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </aside>

      <div className="main">
        <div className="content">{children}</div>
      </div>
    </div>
  )
}
