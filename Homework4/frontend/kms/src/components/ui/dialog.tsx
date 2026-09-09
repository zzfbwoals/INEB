import * as React from 'react'
import * as DialogPrimitive from '@radix-ui/react-dialog'
import { cn } from '@/lib/utils'

/* Radix Dialog — 목업 .modal-bk / .modal 디자인. 접근성(포커스 트랩·ESC)은 Radix 가 담당 */
const Dialog = DialogPrimitive.Root
const DialogTrigger = DialogPrimitive.Trigger
const DialogClose = DialogPrimitive.Close

function DialogContent({
  className,
  title,
  headerExtra,
  children,
  wide,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Content> & { title: string; wide?: boolean; headerExtra?: React.ReactNode }) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="modal-bk" />
      <DialogPrimitive.Content data-slot="dialog-content" className={cn('modal', wide && 'wide', className)} {...props}>
        {/* headerExtra: 제목 바로 아래 고정되는 부가 요소(탭 등) — 본문과 함께 스크롤되지 않는다 */}
        <div className={cn('modal-h', headerExtra && 'has-extra')}>
          <div className="modal-h-main">
            <DialogPrimitive.Title asChild>
              <h3>{title}</h3>
            </DialogPrimitive.Title>
            {headerExtra}
          </div>
          <DialogPrimitive.Description className="sr-only">{title}</DialogPrimitive.Description>
          <DialogPrimitive.Close className="x" aria-label="닫기" data-tip="닫기">
            ✕
          </DialogPrimitive.Close>
        </div>
        {children}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  )
}

function DialogBody({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="dialog-body" className={cn('modal-b', className)} {...props} />
}

function DialogFooter({ className, children, ...props }: React.ComponentProps<'div'>) {
  return (
    <div data-slot="dialog-footer" className={cn('modal-f', className)} {...props}>
      {children}
    </div>
  )
}

export { Dialog, DialogTrigger, DialogClose, DialogContent, DialogBody, DialogFooter }
