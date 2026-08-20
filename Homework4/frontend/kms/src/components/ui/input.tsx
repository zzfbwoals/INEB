import * as React from 'react'
import { cn } from '@/lib/utils'

// shadcn/ui Input — 목업 .input 스타일로 커스터마이징
function Input({ className, type, ...props }: React.ComponentProps<'input'>) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        'w-full rounded-[9px] border border-(--line-strong) bg-(--bg-deep) px-3 py-[9px] text-[13.5px] text-(--text) outline-none transition-all duration-150 placeholder:text-(--text-3) focus:border-(--blue) focus:shadow-[0_0_0_3px_var(--blue-bg)] disabled:opacity-40',
        className,
      )}
      {...props}
    />
  )
}

export { Input }
