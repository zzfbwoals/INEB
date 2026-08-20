// iNEB 브랜드 로고 (목업 shell.js / login.html의 SVG 이식)

/** 풀 로고 — letterFill: 로그인 카드는 currentColor(텍스트색), 사이드바는 브랜드 블루 */
export function LogoFull({
  width = 72,
  height = 22,
  letterFill = '#3B9EFF',
  className,
}: {
  width?: number
  height?: number
  letterFill?: string
  className?: string
}) {
  return (
    <svg
      className={className}
      width={width}
      height={height}
      viewBox="0 0 172 52"
      fill="none"
      aria-label="iNEB"
    >
      <rect x="2" y="20" width="9" height="30" rx="1.5" fill={letterFill} />
      <circle cx="6.5" cy="10" r="6.5" fill="#7CC0FF" />
      <rect x="3.5" y="12" width="6" height="16" rx="1" fill="#7CC0FF" />
      <path d="M24 50V2h9l22 34V2h9v48h-9L33 16v34h-9z" fill={letterFill} />
      <rect x="74" y="2" width="34" height="9" rx="1" fill={letterFill} />
      <rect x="74" y="21.5" width="34" height="9" rx="1" fill={letterFill} />
      <rect x="74" y="41" width="34" height="9" rx="1" fill={letterFill} />
      <path
        d="M118 2h26c9 0 15 5 15 12.5 0 5-2.6 8.6-6.6 10.5 5 1.8 8.6 6 8.6 12C161 45 154.6 50 145 50h-27V2zm9 19.5h16c4 0 6.5-2.2 6.5-5.5s-2.5-5.5-6.5-5.5h-16v11zm0 20h17c4.4 0 7-2.4 7-6s-2.6-6-7-6h-17v12z"
        fill={letterFill}
      />
    </svg>
  )
}

/** 축약 마크 — 사이드바 접힘 상태용 */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg className={className} width="15" height="26" viewBox="0 0 13 52" fill="none">
      <rect x="2" y="20" width="9" height="30" rx="1.5" fill="#3B9EFF" />
      <circle cx="6.5" cy="10" r="6.5" fill="#7CC0FF" />
      <rect x="3.5" y="12" width="6" height="16" rx="1" fill="#7CC0FF" />
    </svg>
  )
}
