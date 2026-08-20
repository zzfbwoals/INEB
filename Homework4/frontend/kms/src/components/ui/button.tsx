import * as React from 'react'
import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

// shadcn/ui Button — 목업 .btn 디자인 시스템으로 variant 커스터마이징
const buttonVariants = cva(
  'inline-flex items-center gap-[7px] whitespace-nowrap rounded-[9px] border border-transparent text-[13.5px] font-semibold transition-all duration-150 outline-none cursor-pointer disabled:pointer-events-none disabled:opacity-40',
  {
    variants: {
      variant: {
        primary: 'bg-(--btn-blue) text-(--btn-blue-tx) hover:bg-(--btn-blue-h)',
        ghost:
          'bg-(--raised) border-(--line-strong) text-(--text-2) hover:bg-(--hover) hover:text-(--text)',
        danger: 'bg-(--red-bg) border-(--red-bg) text-(--red) hover:brightness-110',
      },
      size: {
        default: 'px-4 py-[9px]',
        sm: 'rounded-lg px-[11px] py-1.5 text-[12.5px]',
      },
    },
    defaultVariants: {
      variant: 'primary',
      size: 'default',
    },
  },
)

function Button({
  className,
  variant,
  size,
  asChild = false,
  ...props
}: React.ComponentProps<'button'> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }) {
  const Comp = asChild ? Slot : 'button'
  return (
    <Comp
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}

export { Button, buttonVariants }
