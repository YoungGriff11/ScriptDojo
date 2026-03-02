import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

export default function DashboardPage() {
  const navigate = useNavigate()
  const [files, setFiles] = useState([])
  const [currentUser, setCurrentUser] = useState('')
  const [newFileName, setNewFileName] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // Load user + files on mount
  useEffect(() => {
    loadCurrentUser()
    loadFiles()
  }, [])

  async function loadCurrentUser() {
    try {
      const res = await fetch('/api/user/me', { credentials: 'include' })
      if (res.status === 401) {
        navigate('/login')
        return
      }
      const user = await res.json()
      setCurrentUser(user.username)
    } catch {
      navigate('/login')
    }
  }

  async function loadFiles() {
    try {
      const res = await fetch('/api/files', { credentials: 'include' })
      if (res.status === 401) {
        navigate('/login')
        return
      }
      const data = await res.json()
      setFiles(data)
    } catch {
      setError('Failed to load files')
    } finally {
      setLoading(false)
    }
  }

  async function createFile() {
    if (!newFileName.trim()) return

    let name = newFileName.trim()
    if (!name.endsWith('.java')) name += '.java'

    const className = name.replace('.java', '')

    try {
      const res = await fetch('/api/files', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          name,
          content: `public class ${className} {\n    public static void main(String[] args) {\n        System.out.println("Hello from ${className}!");\n    }\n}`,
          language: 'java',
        }),
      })

      if (res.ok) {
        setNewFileName('')
        loadFiles()
      } else {
        setError('Failed to create file')
      }
    } catch {
      setError('Network error')
    }
  }

  async function deleteFile(id) {
    if (!window.confirm('Delete this file?')) return
    try {
      await fetch(`/api/files/${id}`, {
        method: 'DELETE',
        credentials: 'include',
      })
      loadFiles()
    } catch {
      setError('Failed to delete file')
    }
  }

  function logout() {
    document.cookie = 'JSESSIONID=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'
    navigate('/login')
  }

  function formatDate(dateStr) {
    return new Date(dateStr).toLocaleString()
  }

  return (
    <div style={styles.body}>
      <div style={styles.container}>

        {/* ── Header ── */}
        <div style={styles.header}>
          <h1 style={styles.h1}>ScriptDojo</h1>
          <div style={styles.userInfo}>
            Logged in as: <strong>{currentUser}</strong>
            <button style={styles.logoutBtn} onClick={logout}>Logout</button>
          </div>
        </div>

        {/* ── Create File ── */}
        <div style={styles.newFileRow}>
          <input
            style={styles.input}
            type="text"
            placeholder="New file name (e.g. Main.java)"
            value={newFileName}
            onChange={e => setNewFileName(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && createFile()}
          />
          <button style={styles.createBtn} onClick={createFile}>
            Create File
          </button>
        </div>

        {/* ── Error ── */}
        {error && <div style={styles.errorMsg}>{error}</div>}

        {/* ── File List ── */}
        <div style={styles.fileList}>
          {loading && (
            <div style={styles.empty}>Loading your files...</div>
          )}

          {!loading && files.length === 0 && (
            <div style={styles.empty}>No files yet. Create one above!</div>
          )}

          {!loading && files.map(file => (
            <div key={file.id} style={styles.fileCard}>
              <div style={styles.fileInfo}>
                <span
                  style={styles.fileName}
                  onClick={() => navigate(`/editor?fileId=${file.id}`)}
                >
                  {file.name}
                </span>
                <span style={styles.fileMeta}>
                  last updated {formatDate(file.updatedAt)}
                </span>
              </div>
              <button
                style={styles.deleteBtn}
                onClick={() => deleteFile(file.id)}
              >
                Delete
              </button>
            </div>
          ))}
        </div>

      </div>
    </div>
  )
}

const styles = {
  body: {
    minHeight: '100vh',
    width: '100%',
    background: '#1a1a1a',
    color: '#ddd',
    padding: '20px',
  },
  container: {
    maxWidth: '900px',
    margin: '0 auto',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '30px',
    flexWrap: 'wrap',
    gap: '10px',
  },
  h1: {
    color: '#0f0',
    fontSize: 'clamp(1.8em, 4vw, 2.5em)',
    margin: 0,
  },
  userInfo: {
    color: '#aaa',
    fontSize: '0.9em',
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
  },
  logoutBtn: {
    padding: '8px 16px',
    background: '#f44',
    color: 'white',
    border: 'none',
    borderRadius: '6px',
    cursor: 'pointer',
    fontSize: '0.85em',
    fontWeight: 'bold',
  },
  newFileRow: {
    display: 'flex',
    gap: '10px',
    marginBottom: '20px',
    flexWrap: 'wrap',
  },
  input: {
    flex: 1,
    padding: '12px',
    border: '1px solid #444',
    borderRadius: '6px',
    background: '#333',
    color: '#fff',
    fontSize: '16px',
    minWidth: '200px',
    outline: 'none',
  },
  createBtn: {
    padding: '12px 24px',
    background: '#0f0',
    color: 'black',
    border: 'none',
    borderRadius: '6px',
    cursor: 'pointer',
    fontWeight: 'bold',
    fontSize: '15px',
  },
  errorMsg: {
    padding: '10px',
    background: '#fadbd8',
    color: '#e74c3c',
    borderRadius: '6px',
    marginBottom: '15px',
    fontSize: '0.9em',
  },
  fileList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
  },
  fileCard: {
    background: '#2d2d2d',
    padding: '15px 20px',
    borderRadius: '8px',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: '10px',
  },
  fileInfo: {
    display: 'flex',
    flexDirection: 'column',
    gap: '4px',
  },
  fileName: {
    color: '#0ff',
    fontSize: '1.1em',
    fontWeight: '500',
    cursor: 'pointer',
    textDecoration: 'none',
  },
  fileMeta: {
    color: '#888',
    fontSize: '0.85em',
  },
  deleteBtn: {
    padding: '8px 16px',
    background: '#f44',
    color: 'white',
    border: 'none',
    borderRadius: '6px',
    cursor: 'pointer',
    fontSize: '0.85em',
    fontWeight: 'bold',
  },
  empty: {
    textAlign: 'center',
    color: '#777',
    fontStyle: 'italic',
    padding: '40px 0',
  },
}