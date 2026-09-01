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

/* 목업 shell.js의 아이콘 세트 */
const icons = {
  dash: (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <rect x="3" y="3" width="8" height="8" rx="2" />
      <rect x="13" y="3" width="8" height="5" rx="2" />
      <rect x="13" y="10" width="8" height="11" rx="2" />
      <rect x="3" y="13" width="8" height="8" rx="2" />
    </svg>
  ),
  key: (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="7.5" cy="15.5" r="5" />
      <path d="m11 12 9.6-9.6" />
      <path d="m15.2 7.8 3 3L22 7l-3-3" />
    </svg>
  ),
  test: (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M9 3h6M10 3v6L4.5 18a2.4 2.4 0 0 0 2.1 3.5h10.8a2.4 2.4 0 0 0 2.1-3.5L14 9V3" />
    </svg>
  ),
  user: (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6" />
      <circle cx="17" cy="9" r="2.6" />
      <path d="M16.5 14.2c3 .3 5 2.5 5 5.3" />
    </svg>
  ),
  notice: (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M4 5h16v12H8l-4 4V5z" />
      <path d="M8 9h8M8 12.5h5" />
    </svg>
  ),
  audit: (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M12 3 4.5 6v5c0 5 3.2 8.6 7.5 10 4.3-1.4 7.5-5 7.5-10V6L12 3z" />
      <path d="m9 11.5 2.2 2.2L15.5 9" />
    </svg>
  ),
  sun: (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>
  ),
  moon: (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M20.5 14.5A8.5 8.5 0 1 1 9.5 3.5a7 7 0 0 0 11 11z" />
    </svg>
  ),
  sys: (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <rect x="3" y="4" width="18" height="13" rx="2" />
      <path d="M9 21h6m-3-4v4" />
    </svg>
  ),
  out: (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M14 4h-8v16h8M10 12h11m0 0-3.5-3.5M21 12l-3.5 3.5" />
    </svg>
  ),
  chk: (
    <svg className="chk" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6">
      <path d="m4.5 12.5 5 5 10-11" />
    </svg>
  ),
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
      { key: 'notices', label: '공지사항', icon: icons.notice },
      { key: 'audit', label: '감사 로그', icon: icons.audit, to: '/audit' },
    ],
  },
]

const THEME_ITEMS: { mode: ThemeMode; label: string; icon: ReactNode }[] = [
  { mode: 'light', label: '라이트 모드', icon: icons.sun },
  { mode: 'dark', label: '다크 모드', icon: icons.moon },
  { mode: 'system', label: '시스템 설정', icon: icons.sys },
]

export default function AppLayout({ crumb, children }: { crumb?: string; children: ReactNode }) {
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
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="4" width="18" height="16" rx="2.5" />
              <path d="M9.5 4v16" />
            </svg>
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
                <svg className="sp-chev" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
                  <path d="m7 14.5 5-5 5 5" />
                </svg>
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
        <header className="topbar">
          <div className="crumb">{crumb ? `홈 / ${crumb}` : '홈'}</div>
          <div className="sp" />
        </header>
        <div className="content">{children}</div>
      </div>
    </div>
  )
}
