import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import WelcomePage   from './pages/WelcomePage'
import LoginPage     from './pages/LoginPage'
import SignupPage    from './pages/SignupPage'
import DashboardPage from './pages/DashboardPage'
import EditorPage    from './pages/EditorPage'
import GuestPage     from './pages/GuestPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/"           element={<WelcomePage />} />
        <Route path="/login"      element={<LoginPage />} />
        <Route path="/signup"     element={<SignupPage />} />
        <Route path="/dashboard"  element={<DashboardPage />} />
        <Route path="/editor"     element={<EditorPage />} />
        <Route path="/room-guest" element={<GuestPage />} />

        {/* Catch-all: redirect unknown routes to welcome */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App