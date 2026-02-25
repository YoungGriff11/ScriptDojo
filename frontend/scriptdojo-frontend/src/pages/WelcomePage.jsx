import { useNavigate } from 'react-router-dom'

export default function WelcomePage() {
  const navigate = useNavigate()

  return (
    <div style={styles.body}>
      <div style={styles.box}>
        <h1 style={styles.h1}>ScriptDojo</h1>
        <p style={styles.subtitle}>Real-time Collaborative Java IDE</p>
        <p style={styles.tagline}>Like Google Docs... but for Java</p>
        <div style={styles.buttonRow}>
          <button
            style={{ ...styles.button, ...styles.loginBtn }}
            onClick={() => navigate('/login')}
          >
            Login
          </button>
          <button
            style={{ ...styles.button, ...styles.signupBtn }}
            onClick={() => navigate('/signup')}
          >
            Sign Up Free
          </button>
        </div>
      </div>
    </div>
  )
}

const styles = {
  body: {
    fontFamily: 'Segoe UI, sans-serif',
    background: 'linear-gradient(135deg, #0f0f0f, #001a00)',
    color: '#0f0',
    minHeight: '100vh',
    width: '100%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '20px',
  },
  box: {
    textAlign: 'center',
    background: '#111',
    padding: '60px 80px',
    borderRadius: '20px',
    boxShadow: '0 0 30px #0f0',
    border: '2px solid #0f0',
    width: '100%',
    maxWidth: '550px',
  },
  h1: {
    fontSize: 'clamp(2em, 5vw, 3.5em)',
    margin: '0 0 15px 0',
    textShadow: '0 0 20px #0f0',
  },
  subtitle: {
    fontSize: 'clamp(1em, 2vw, 1.3em)',
    margin: '0 0 8px 0',
    color: '#afa',
  },
  tagline: {
    fontSize: 'clamp(1em, 2vw, 1.3em)',
    margin: '0 0 30px 0',
    color: '#afa',
  },
  buttonRow: {
    display: 'flex',
    justifyContent: 'center',
    gap: '20px',
    flexWrap: 'wrap',
  },
  button: {
    padding: '15px 40px',
    fontSize: 'clamp(1em, 2vw, 1.2em)',
    border: 'none',
    borderRadius: '10px',
    cursor: 'pointer',
    fontWeight: 'bold',
    transition: 'opacity 0.2s',
  },
  loginBtn: {
    background: '#0ff',
    color: 'black',
  },
  signupBtn: {
    background: '#0f0',
    color: 'black',
  },
}