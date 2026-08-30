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
  children,
  wide,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Content> & { title: string; wide?: boolean }) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="modal-bk" />
      <DialogPrimitive.Content data-slot="dialog-content" className={cn('modal', wide && 'wide', className)} {...props}>
        <div className="modal-h">
          <DialogPrimitive.Title asChild>
            <h3>{title}</h3>
          </DialogPrimitive.Title>
          <DialogPrimitive.Description className="sr-only">{title}</DialogPrimitive.Description>
          <DialogPrimitive.Close className="x" aria-label="닫기">
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
