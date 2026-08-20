import * as React from 'react'
import * as LabelPrimitive from '@radix-ui/react-label'
import { cn } from '@/lib/utils'

// shadcn/ui Label — 목업 .field label 스타일
function Label({ className, ...props }: React.ComponentProps<typeof LabelPrimitive.Root>) {
  return (
    <LabelPrimitive.Root
      data-slot="label"
      className={cn('text-[12.5px] font-semibold text-(--text-2)', className)}
      {...props}
    />
  )
}

export { Label }
