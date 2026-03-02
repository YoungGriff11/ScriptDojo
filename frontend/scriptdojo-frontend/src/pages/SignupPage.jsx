import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'

export default function SignupPage() {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setLoading(true)
    setError('')
    setSuccess('')

    const username = e.target.username.value.trim()
    const password = e.target.password.value
    const email    = e.target.email.value.trim()

    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, email }),
      })

      const text = await res.text()

      if (res.ok) {
        setSuccess(text)
        // Redirect to login after 1 second
        setTimeout(() => navigate('/login'), 1000)
      } else {
        setError(text)
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
        <p style={styles.subtitle}>Create your account</p>

        <form onSubmit={handleSubmit} style={styles.form}>
          <input
            style={styles.input}
            type="text"
            name="username"
            placeholder="Username (3–20 chars)"
            required
          />
          <input
            style={styles.input}
            type="password"
            name="password"
            placeholder="Password (6+ chars)"
            required
          />
          <input
            style={styles.input}
            type="email"
            name="email"
            placeholder="Email"
            required
          />
          <button style={styles.button} type="submit" disabled={loading}>
            {loading ? 'Creating account...' : 'Sign Up'}
          </button>
        </form>

        {error   && <div style={{ ...styles.message, ...styles.errorMsg  }}>{error}</div>}
        {success && <div style={{ ...styles.message, ...styles.successMsg }}>{success}</div>}

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
    background: '#3498db',
    color: 'white',
    border: 'none',
    borderRadius: '6px',
    fontSize: '16px',
    cursor: 'pointer',
    fontWeight: 'bold',
    marginTop: '4px',
  },
  message: {
    marginTop: '15px',
    padding: '10px',
    borderRadius: '6px',
    textAlign: 'center',
    fontSize: '0.9em',
  },
  errorMsg: {
    background: '#fadbd8',
    color: '#e74c3c',
  },
  successMsg: {
    background: '#d5efde',
    color: '#27ae60',
  },
  link: {
    display: 'block',
    textAlign: 'center',
    marginTop: '20px',
    color: '#e67e22',
    fontSize: '0.95em',
  },
}