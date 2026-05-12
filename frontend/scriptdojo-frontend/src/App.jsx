import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import WelcomePage   from './pages/WelcomePage'
import LoginPage     from './pages/LoginPage'
import SignupPage    from './pages/SignupPage'
import DashboardPage from './pages/DashboardPage'
import EditorPage    from './pages/EditorPage'
import GuestPage     from './pages/GuestPage'

/**
 * Root application component that defines the client-side routing structure
 * for ScriptDojo using React Router v6.
 * All routes listed here must also be registered in SpaController and the
 * SecurityConfig permitAll() block on the backend so that direct navigation
 * and page refreshes are handled correctly by Spring Boot.
 * Route structure:
 *   /              — WelcomePage: landing page for unauthenticated visitors
 *   /login         — LoginPage: form login for registered users
 *   /signup        — SignupPage: new account registration
 *   /dashboard     — DashboardPage: authenticated host's file management view
 *   /editor        — EditorPage: standalone editor for the authenticated host
 *   /room-guest    — GuestPage: legacy guest entry point (kept for compatibility)
 *   /room/:roomId  — GuestPage: primary guest entry point via shareable room URL
 *   *              — Catch-all redirect to / for any unrecognised path
 */
function App() {
  return (
    // BrowserRouter enables HTML5 history-based navigation so URLs are clean
    // (no hash fragments) and match the paths registered in SpaController
    <BrowserRouter>
      <Routes>
        <Route path="/"           element={<WelcomePage />} />
        <Route path="/login"      element={<LoginPage />} />
        <Route path="/signup"     element={<SignupPage />} />
        <Route path="/dashboard"  element={<DashboardPage />} />
        <Route path="/editor"     element={<EditorPage />} />

        {/* Legacy guest route — preserved for backwards compatibility */}
        <Route path="/room-guest" element={<GuestPage />} />

        {/* Primary guest route — roomId is extracted and used to fetch room
            data from GET /api/room/join/{roomId} on page load */}
        <Route path="/room/:roomId" element={<GuestPage />} />

        {/* Catch-all: any unrecognised path redirects to the welcome page.
            replace prevents the unknown path from being added to browser history */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App