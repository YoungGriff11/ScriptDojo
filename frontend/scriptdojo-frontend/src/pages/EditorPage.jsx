import { useState, useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import Editor from '@monaco-editor/react'

export default function EditorPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const fileId = searchParams.get('fileId')

  const [fileName, setFileName]     = useState('Loading...')
  const [code, setCode]             = useState('// Loading...')
  const [currentUser, setCurrentUser] = useState('')
  const [connected]   = useState(false)
  const [output, setOutput]         = useState('Ready to run Java code...')
  const [outputColor, setOutputColor] = useState('#0f0')
  const [shareUrl, setShareUrl]     = useState('')
  const [showShareLink, setShowShareLink] = useState(false)

  const editorRef = useRef(null)

  

  async function loadCurrentUser() {
    try {
      const res = await fetch('/api/user/me', { credentials: 'include' })
      if (res.status === 401) { navigate('/login'); return }
      const user = await res.json()
      setCurrentUser(user.username)
    } catch {
      navigate('/login')
    }
  }

  async function loadFile() {
    try {
      const res = await fetch(`/api/files/${fileId}`, { credentials: 'include' })
      if (res.status === 401) { navigate('/login'); return }
      const data = await res.json()
      setFileName(data.name)
      setCode(data.content)
    } catch {
      setCode('// Error loading file')
    }
  }

  async function saveFile() {
    try {
      const res = await fetch(`/api/files/${fileId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ content: code }),
      })
      if (res.ok) alert('✅ File saved successfully!')
      else alert('❌ Failed to save file')
    } catch {
      alert('❌ Network error saving file')
    }
  }

  async function runCode() {
  setOutput('⏳ Compiling and running...')
  setOutputColor('#0ff')

  try {
    const res = await fetch('/api/compiler/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ code, fileId }),
    })
    const data = await res.json()

    if (data.success) {
      setOutputColor('#0f0')
      setOutput(
        `✅ EXECUTION SUCCESSFUL (${data.executionResult.executionTimeMs}ms)\n\nOutput:\n` +
        (data.executionResult.output || '(no output)')
      )
    } else if (data.stage === 'compilation') {
      displayCompilationErrors(data.compilationResult)
    } else {
      displayExecutionError(data.executionResult)
    }
  } catch {
    setOutputColor('#f44')
    setOutput('❌ Network error running code')
  }
}

  function displayCompilationErrors(result) {
    setOutputColor('#f44')
    let text = '❌ COMPILATION FAILED\n\n'
    if (result.errorMessage) text += result.errorMessage + '\n\n'
    if (result.errors?.length > 0) {
      text += 'Errors:\n'
      result.errors.forEach(e => { text += `  Line ${e.lineNumber}: ${e.message}\n` })
    }
    setOutput(text)
  }

  function displayExecutionError(result) {
    setOutputColor('#f44')
    setOutput(`❌ EXECUTION FAILED\n\nError:\n${result.error || result.exceptionMessage || 'Unknown error'}\n\nExit code: ${result.exitCode}`)
  }

  async function generateShareLink() {
    try {
      const res = await fetch(`/api/room/create?fileId=${fileId}`, {
        method: 'POST',
        credentials: 'include',
      })
      const data = await res.json()
      if (data.url) {
        setShareUrl(data.url)
        setShowShareLink(true)
      } else {
        alert('❌ Failed to generate share link')
      }
    } catch {
      alert('❌ Network error generating share link')
    }
  }

  function copyShareLink() {
    navigator.clipboard.writeText(shareUrl)
      .then(() => alert('✅ Link copied to clipboard!'))
      .catch(() => alert('❌ Failed to copy link'))
  }

  function handleEditorMount(editor) {
    editorRef.current = editor
  }
// ── Load user + file on mount ──────────────────────
  useEffect(() => {
    if (!fileId) {
      navigate('/dashboard')
      return
    }
    loadCurrentUser()
    loadFile()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fileId])
  return (
    <div style={styles.container}>

      {/* ── Sidebar ── */}
      <div style={styles.sidebar}>
        <h2 style={styles.sidebarTitle}>
          <span style={{
            ...styles.statusDot,
            background: connected ? '#0f0' : '#f44',
            boxShadow: connected ? '0 0 8px #0f0' : '0 0 8px #f44',
          }} />
          ScriptDojo
        </h2>

        <div style={styles.infoBox}>
          <strong style={styles.infoLabel}>File:</strong> {fileName}
        </div>
        <div style={styles.infoBox}>
          <strong style={styles.infoLabel}>Owner:</strong> {currentUser || 'You'}
        </div>

        <button style={styles.btn} onClick={saveFile}>💾 Save File</button>
        <button style={styles.btn} onClick={runCode}>▶️ Run Java Code</button>
        <button style={styles.btn} onClick={generateShareLink}>🔗 Generate Share Link</button>

        {showShareLink && (
          <div style={styles.shareLinkBox}>
            <h3 style={styles.shareLinkTitle}>Share Link</h3>
            <input
              style={styles.shareLinkInput}
              type="text"
              value={shareUrl}
              readOnly
            />
            <button style={styles.btn} onClick={copyShareLink}>📋 Copy Link</button>
          </div>
        )}

        {/* Users panel — will be populated in Part 2 */}
        <div style={styles.usersBox}>
          <h3 style={styles.usersTitle}>👥 Active Users</h3>
          <div style={{ color: '#888', fontSize: '0.9em' }}>
            {connected ? 'Connected' : 'Connecting...'}
          </div>
        </div>
      </div>

      {/* ── Editor + Console ── */}
      <div style={styles.editorContainer}>
        <div style={styles.editorWrapper}>
          <Editor
            height="100%"
            defaultLanguage="java"
            theme="vs-dark"
            value={code}
            onChange={value => setCode(value || '')}
            onMount={handleEditorMount}
            options={{
              fontSize: 14,
              minimap: { enabled: true },
              automaticLayout: true,
            }}
          />
        </div>

        {/* ── Console ── */}
        <div style={styles.consoleContainer}>
          <div style={styles.consoleHeader}>
            <h3 style={styles.consoleTitle}>📟 Console Output</h3>
            <button
              style={styles.clearBtn}
              onClick={() => { setOutput('Ready to run Java code...'); setOutputColor('#0f0') }}
            >
              Clear
            </button>
          </div>
          <pre style={{ ...styles.consoleOutput, color: outputColor }}>
            {output}
          </pre>
        </div>
      </div>

    </div>
  )
}

const styles = {
  container: {
    display: 'flex',
    height: '100vh',
    width: '100%',
    background: '#1e1e1e',
    color: '#d4d4d4',
    overflow: 'hidden',
  },

  // ── Sidebar ──
  sidebar: {
    width: '280px',
    minWidth: '280px',
    background: '#252526',
    borderRight: '1px solid #3c3c3c',
    padding: '20px',
    overflowY: 'auto',
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
  },
  sidebarTitle: {
    color: '#0f0',
    marginBottom: '12px',
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    fontSize: '1.2em',
  },
  statusDot: {
    width: '10px',
    height: '10px',
    borderRadius: '50%',
    display: 'inline-block',
    flexShrink: 0,
  },
  infoBox: {
    padding: '10px',
    background: '#2d2d30',
    borderRadius: '4px',
    fontSize: '0.9em',
    marginBottom: '4px',
  },
  infoLabel: {
    color: '#0ff',
  },
  btn: {
    width: '100%',
    padding: '11px',
    background: '#0e639c',
    color: 'white',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '14px',
    fontWeight: 'bold',
    textAlign: 'left',
  },
  shareLinkBox: {
    padding: '10px',
    background: '#2d2d30',
    borderRadius: '4px',
    marginTop: '4px',
  },
  shareLinkTitle: {
    color: '#0ff',
    fontSize: '0.9em',
    marginBottom: '8px',
  },
  shareLinkInput: {
    width: '100%',
    padding: '7px',
    background: '#1e1e1e',
    border: '1px solid #3c3c3c',
    color: '#d4d4d4',
    borderRadius: '3px',
    fontFamily: 'monospace',
    fontSize: '11px',
    marginBottom: '6px',
    boxSizing: 'border-box',
  },
  usersBox: {
    marginTop: '12px',
    padding: '10px',
    background: '#2d2d30',
    borderRadius: '4px',
  },
  usersTitle: {
    color: '#0ff',
    fontSize: '0.9em',
    marginBottom: '8px',
  },

  // ── Editor area ──
  editorContainer: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  editorWrapper: {
    flex: 1,
    overflow: 'hidden',
  },

  // ── Console ──
  consoleContainer: {
    height: '220px',
    minHeight: '220px',
    background: '#1e1e1e',
    borderTop: '1px solid #3c3c3c',
    display: 'flex',
    flexDirection: 'column',
  },
  consoleHeader: {
    background: '#2d2d30',
    padding: '8px 15px',
    borderBottom: '1px solid #3c3c3c',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  consoleTitle: {
    color: '#0f0',
    fontSize: '13px',
    margin: 0,
  },
  clearBtn: {
    padding: '4px 12px',
    background: '#3c3c3c',
    color: '#d4d4d4',
    border: 'none',
    borderRadius: '3px',
    cursor: 'pointer',
    fontSize: '12px',
  },
  consoleOutput: {
    flex: 1,
    background: '#000',
    padding: '12px 15px',
    fontFamily: 'Courier New, monospace',
    fontSize: '0.85em',
    overflowY: 'auto',
    margin: 0,
    whiteSpace: 'pre-wrap',
    wordWrap: 'break-word',
  },
}