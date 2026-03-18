import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'

export default function SignupPage() {
  const navigate = useNavigate()
  const [error, setError]     = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(false)
  const [fieldErrors, setFieldErrors] = useState({
    username: '',
    password: '',
    email: '',
  })

  // ── Client-side validation ───────────────────────────
  function validate(username, password, email) {
    const errors = { username: '', password: '', email: '' }
    let valid = true

    if (!username) {
      errors.username = 'Username is required.'
      valid = false
    } else if (username.length < 3) {
      errors.username = 'Username must be at least 3 characters.'
      valid = false
    } else if (username.length > 20) {
      errors.username = 'Username must be 20 characters or fewer.'
      valid = false
    } else if (!/^[a-zA-Z0-9_]+$/.test(username)) {
      errors.username = 'Username can only contain letters, numbers and underscores.'
      valid = false
    }

    if (!password) {
      errors.password = 'Password is required.'
      valid = false
    } else if (password.length < 6) {
      errors.password = 'Password must be at least 6 characters.'
      valid = false
    }

    if (!email) {
      errors.email = 'Email is required.'
      valid = false
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      errors.email = 'Please enter a valid email address.'
      valid = false
    }

    return { errors, valid }
  }

  // ── Map backend error messages to friendly text ──────
  function parseBackendError(text) {
    const lower = text.toLowerCase()
    if (lower.includes('username') && (lower.includes('taken') || lower.includes('exists') || lower.includes('already'))) {
      return 'That username is already taken. Please choose a different one.'
    }
    if (lower.includes('email') && (lower.includes('taken') || lower.includes('exists') || lower.includes('already'))) {
      return 'An account with that email already exists.'
    }
    if (lower.includes('username')) {
      return 'Invalid username. Please check and try again.'
    }
    if (lower.includes('password')) {
      return 'Invalid password. Please check the requirements and try again.'
    }
    if (lower.includes('email')) {
      return 'Invalid email address. Please check and try again.'
    }
    // Fallback — show the raw backend message
    return text || 'Registration failed. Please try again.'
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSuccess('')
    setFieldErrors({ username: '', password: '', email: '' })

    const username = e.target.username.value.trim()
    const password = e.target.password.value
    const email    = e.target.email.value.trim()

    // Client-side validation first
    const { errors, valid } = validate(username, password, email)
    if (!valid) {
      setFieldErrors(errors)
      return
    }

    setLoading(true)

    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, email }),
      })

      const text = await res.text()

      if (res.ok) {
        setSuccess(`✅ Account created successfully! Welcome, ${username}. Redirecting to login...`)
        setTimeout(() => navigate('/login'), 2000)
      } else if (res.status === 409) {
        // 409 Conflict = duplicate username or email
        setError(parseBackendError(text))
      } else if (res.status === 400) {
        // 400 Bad Request = validation error from backend
        setError(parseBackendError(text))
      } else if (res.status >= 500) {
        setError('Server error. Please try again in a moment.')
      } else {
        setError(parseBackendError(text))
      }
    } catch {
      setError('Unable to connect to the server. Please check your connection.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.body}>
      <div style={styles.container}>
        <h1 style={styles.h1}>ScriptDojo</h1>
        <p style={styles.subtitle}>Create your account</p>

        <form onSubmit={handleSubmit} style={styles.form}>

          {/* ── Username ── */}
          <div style={styles.fieldGroup}>
            <input
              style={{
                ...styles.input,
                border: fieldErrors.username ? '1px solid #e74c3c' : '1px solid #ddd',
              }}
              type="text"
              name="username"
              placeholder="Username (3–20 chars)"
              required
            />
            {fieldErrors.username && (
              <span style={styles.fieldError}>{fieldErrors.username}</span>
            )}
          </div>

          {/* ── Password ── */}
          <div style={styles.fieldGroup}>
            <input
              style={{
                ...styles.input,
                border: fieldErrors.password ? '1px solid #e74c3c' : '1px solid #ddd',
              }}
              type="password"
              name="password"
              placeholder="Password (6+ chars)"
              required
            />
            {fieldErrors.password && (
              <span style={styles.fieldError}>{fieldErrors.password}</span>
            )}
          </div>

          {/* ── Email ── */}
          <div style={styles.fieldGroup}>
            <input
              style={{
                ...styles.input,
                border: fieldErrors.email ? '1px solid #e74c3c' : '1px solid #ddd',
              }}
              type="email"
              name="email"
              placeholder="Email"
              required
            />
            {fieldErrors.email && (
              <span style={styles.fieldError}>{fieldErrors.email}</span>
            )}
          </div>

          <button style={{
            ...styles.button,
            opacity: loading ? 0.7 : 1,
            cursor: loading ? 'not-allowed' : 'pointer',
          }} type="submit" disabled={loading}>
            {loading ? 'Creating account...' : 'Sign Up'}
          </button>
        </form>

        {/* ── Global messages ── */}
        {error && (
          <div style={{ ...styles.message, ...styles.errorMsg }}>
            ❌ {error}
          </div>
        )}
        {success && (
          <div style={{ ...styles.message, ...styles.successMsg }}>
            {success}
          </div>
        )}

        <Link to="/login" style={styles.link}>
          Already have an account? Log in
        </Link>
      </div>
    </div>
  )
}

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
    gap: '4px',
  },
  fieldGroup: {
    display: 'flex',
    flexDirection: 'column',
    gap: '4px',
    marginBottom: '8px',
  },
  input: {
    width: '100%',
    padding: '12px',
    borderRadius: '6px',
    fontSize: '16px',
    outline: 'none',
    boxSizing: 'border-box',
    transition: 'border-color 0.2s',
  },
  fieldError: {
    color: '#e74c3c',
    fontSize: '0.8em',
    paddingLeft: '4px',
  },
  button: {
    width: '100%',
    padding: '12px',
    background: '#3498db',
    color: 'white',
    border: 'none',
    borderRadius: '6px',
    fontSize: '16px',
    fontWeight: 'bold',
    marginTop: '4px',
    transition: 'opacity 0.2s',
  },
  message: {
    marginTop: '15px',
    padding: '12px',
    borderRadius: '6px',
    textAlign: 'center',
    fontSize: '0.9em',
    lineHeight: '1.4',
  },
  errorMsg: {
    background: '#fadbd8',
    color: '#e74c3c',
  },
  successMsg: {
    background: '#d5efde',
    color: '#27ae60',
    fontWeight: '500',
  },
  link: {
    display: 'block',
    textAlign: 'center',
    marginTop: '20px',
    color: '#e67e22',
    fontSize: '0.95em',
  },
}