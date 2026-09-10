import { type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { getToken } from '@/lib/auth'
import { ToastProvider } from '@/components/ui/toast'
import LoginPage from '@/pages/LoginPage'
import DashboardPage from '@/pages/DashboardPage'
import KeyListPage from '@/pages/KeyListPage'
import KeyDetailPage from '@/pages/KeyDetailPage'
import KeyTestPage from '@/pages/KeyTestPage'
import UserListPage from '@/pages/UserListPage'
import AuditLogPage from '@/pages/AuditLogPage'
import NoticeListPage from '@/pages/NoticeListPage'
import NoticeDetailPage from '@/pages/NoticeDetailPage'

function RequireAuth({ children }: { children: ReactNode }) {
  if (!getToken()) {
    return <Navigate to="/login" replace />
  }
  return children
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<RequireAuth><DashboardPage /></RequireAuth>} />
          <Route path="/keys" element={<RequireAuth><KeyListPage /></RequireAuth>} />
          <Route path="/keys/test" element={<RequireAuth><KeyTestPage /></RequireAuth>} />
          <Route path="/keys/:keyUid" element={<RequireAuth><KeyDetailPage /></RequireAuth>} />
          <Route path="/users" element={<RequireAuth><UserListPage /></RequireAuth>} />
          <Route path="/notices" element={<RequireAuth><NoticeListPage /></RequireAuth>} />
          <Route path="/notices/:id" element={<RequireAuth><NoticeDetailPage /></RequireAuth>} />
          <Route path="/audit" element={<RequireAuth><AuditLogPage /></RequireAuth>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </ToastProvider>
    </BrowserRouter>
  )
}
