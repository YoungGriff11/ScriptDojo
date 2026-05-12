import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'

/**
 * Login page for ScriptDojo.
 * Submits credentials to Spring Security's form login endpoint (/perform_login)
 * and verifies the session was established before navigating to /dashboard.
 * Spring Security responds to /perform_login with a redirect rather than a
 * standard JSON response, so redirect: 'manual' is used to prevent the browser
 * from following the redirect automatically. A secondary GET /api/user/me check
 * is then used to confirm the session cookie was set correctly before navigating.
 */
export default function LoginPage() {
  const navigate = useNavigate()
  const [error, setError]     = useState('')
  const [loading, setLoading] = useState(false)

  /**
   * Handles form submission by posting credentials to /perform_login as
   * URL-encoded form data, which is the format Spring Security's form login
   * processing expects.
   * Because Spring Security responds with a 302 redirect on both success and
   * failure, the response type rather than status code is used to detect the
   * outcome. A secondary /api/user/me request confirms the session is valid
   * before navigating, catching the case where credentials were wrong but the
   * response still appeared redirect-like.
   * @param {React.FormEvent} e - the form submit event
   */
  async function handleSubmit(e) {
    e.preventDefault()
    setLoading(true)
    setError('')

    const username = e.target.username.value.trim()
    const password = e.target.password.value

    // Client-side validation before hitting the network
    if (!username) {
      setError('Please enter your username.')
      setLoading(false)
      return
    }
    if (!password) {
      setError('Please enter your password.')
      setLoading(false)
      return
    }

    // Spring Security's /perform_login expects application/x-www-form-urlencoded
    const formData = new URLSearchParams()
    formData.append('username', username)
    formData.append('password', password)

    try {
      const res = await fetch('/perform_login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        credentials: 'include',
        body: formData,
        // Prevent the browser from following the Spring Security redirect —
        // we handle navigation manually after verifying the session
        redirect: 'manual',
      })

      // Spring Security returns a redirect on both success and failure.
      // opaqueredirect is the fetch API's type for a manually-intercepted redirect.
      if (res.type === 'opaqueredirect' || res.status === 302 || res.status === 200) {
        // Verify the login actually succeeded by checking whether the session
        // cookie grants access to an authenticated endpoint
        const check = await fetch('/api/user/me', { credentials: 'include' })
        if (check.ok) {
          navigate('/dashboard')
        } else {
          setError('Invalid username or password. Please try again.')
        }
      } else {
        setError('Invalid username or password. Please try again.')
      }
    } catch {
      setError('Invalid username or password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.body}>
      <div style={styles.container}>
        <h1 style={styles.h1}>ScriptDojo</h1>
        <p style={styles.subtitle}>Log in to collaborate</p>

        <form onSubmit={handleSubmit} style={styles.form}>
          <input
            style={styles.input}
            type="text"
            name="username"
            placeholder="Username"
            required
          />
          <input
            style={styles.input}
            type="password"
            name="password"
            placeholder="Password"
            required
          />
          {/* Button is disabled during the login request to prevent double submission */}
          <button style={styles.button} type="submit" disabled={loading}>
            {loading ? 'Logging in...' : 'Log In'}
          </button>
        </form>

        {error && <div style={styles.errorMsg}>{error}</div>}

        <Link to="/signup" style={styles.link}>
          Don't have an account? Sign up
        </Link>
      </div>
    </div>
  )
}

// ── Inline styles ─────────────────────────────────────────────────────────────
// Defined outside the component to prevent object recreation on every render.
const styles = {
  body: {
    minHeight: '100vh',
    width: '100%',
    background: '#f4f7fa',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '20px',
  },
  container: {
    width: '100%',
    maxWidth: '420px',
    background: 'white',
    padding: '40px',
    borderRadius: '12px',
    boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
  },
  h1: {
    textAlign: 'center',
    color: '#2c3e50',
    marginBottom: '8px',
    fontSize: 'clamp(1.8em, 4vw, 2.2em)',
  },
  subtitle: {
    textAlign: 'center',
    color: '#7f8c8d',
    marginBottom: '30px',
    fontSize: '1em',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
  },
  input: {
    width: '100%',
    padding: '12px',
    border: '1px solid #ddd',
    borderRadius: '6px',
    fontSize: '16px',
    outline: 'none',
    boxSizing: 'border-box',
  },
  button: {
    width: '100%',
    padding: '12px',
    background: '#27ae60',
    color: 'white',
    border: 'none',
    borderRadius: '6px',
    fontSize: '16px',
    cursor: 'pointer',
    fontWeight: 'bold',
    marginTop: '4px',
  },
  errorMsg: {
    marginTop: '15px',
    padding: '10px',
    borderRadius: '6px',
    textAlign: 'center',
    background: '#fadbd8',
    color: '#e74c3c',
    fontSize: '0.9em',
  },
  link: {
    display: 'block',
    textAlign: 'center',
    marginTop: '20px',
    color: '#e67e22',
    fontSize: '0.95em',
  },
}