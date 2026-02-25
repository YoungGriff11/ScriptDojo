import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'

export default function LoginPage() {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setLoading(true)
    setError('')

    const username = e.target.username.value
    const password = e.target.password.value

    // Spring Security expects form-encoded data, not JSON
    const formData = new URLSearchParams()
    formData.append('username', username)
    formData.append('password', password)

    try {
      const res = await fetch('/perform_login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        credentials: 'include',
        body: formData,
        redirect: 'manual', // prevent browser auto-following the redirect
      })

      // Spring returns a redirect (302) on success
      if (res.status === 200 || res.status === 302 || res.type === 'opaqueredirect') {
        navigate('/dashboard')
      } else {
        setError('Invalid username or password.')
      }
    } catch {
      setError('Network error. Is the backend running?')
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