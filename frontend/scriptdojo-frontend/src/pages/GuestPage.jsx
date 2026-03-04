import { useState, useEffect, useRef } from 'react'
import { useParams } from 'react-router-dom'
import Editor from '@monaco-editor/react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export default function GuestPage() {
  const { roomId } = useParams()

  const [fileId, setFileId]           = useState(null)
  const [fileName, setFileName]       = useState('')
  const [guestName, setGuestName]     = useState('')
  const [code, setCode]               = useState('// Loading...')
  const [connected, setConnected]     = useState(false)
  const [hasEditPermission, setHasEditPermission] = useState(false)
  const [activeUsers, setActiveUsers] = useState([])
  const [output, setOutput]           = useState('Waiting for host to run code...')
  const [outputColor, setOutputColor] = useState('#0f0')
  const [loading, setLoading]         = useState(true)
  const [error, setError]             = useState('')

  const editorRef      = useRef(null)
  const stompRef       = useRef(null)
  const isRemoteChange = useRef(false)
  const latestErrors   = useRef([])
  const codeRef        = useRef('')
  const hasEditRef     = useRef(false)
  const guestNameRef   = useRef('')
  const fileIdRef      = useRef(null)

  // Cursor presence
  const cursorDecorations = useRef(new Map())
  const userColors        = useRef(new Map())
  const colorIndex        = useRef(0)
  const cursorSendTimer   = useRef(null)

  const CURSOR_COLORS = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
    '#DDA0DD', '#98D8C8', '#F39C12', '#A29BFE', '#FD79A8',
  ]

  // ── Fetch room data on mount ─────────────────────────
  useEffect(() => {
    if (!roomId) { setError('No room ID provided'); return }

    fetch(`/api/room/join/${roomId}`)
      .then(res => {
        if (!res.ok) throw new Error('Room not found')
        return res.json()
      })
      .then(data => {
        const decoded = atob(data.content)
        setFileId(data.fileId)
        setFileName(data.fileName)
        setGuestName(data.guestName)
        setCode(decoded)
        fileIdRef.current   = data.fileId
        guestNameRef.current = data.guestName
        codeRef.current     = decoded
        setLoading(false)
      })
      .catch(() => {
        setError('Room not found or has expired.')
        setLoading(false)
      })
  }, [roomId])

  // ── Connect WebSocket after room data is loaded ──────
  useEffect(() => {
    if (!fileId || !guestName) return
    connectWebSocket()
    return () => {
      if (stompRef.current) stompRef.current.deactivate()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fileId, guestName])

  function connectWebSocket() {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: { username: guestNameRef.current },
      reconnectDelay: 5000,

      onConnect: () => {
        console.log('✅ Guest WebSocket connected as:', guestNameRef.current)
        setConnected(true)

        // ── Room edits ────────────────────────────────
        client.subscribe(`/topic/room/${fileIdRef.current}`, (message) => {
          const data = JSON.parse(message.body)
          if (data.username !== guestNameRef.current) {
            isRemoteChange.current = true
            setCode(data.content)
            codeRef.current = data.content
            setTimeout(() => {
              if (latestErrors.current.length > 0) {
                highlightSyntaxErrors(latestErrors.current)
              } else {
                clearSyntaxErrors()
              }
            }, 50)
          }
        })

        // ── Cursor positions ──────────────────────────
        client.subscribe(`/topic/room/${fileIdRef.current}/cursors`, (message) => {
          const data = JSON.parse(message.body)
          if (data.username === guestNameRef.current) return
          renderRemoteCursor(data.username, data.line, data.column)
        })

        // ── Permission updates ────────────────────────
        client.subscribe(`/topic/room/${fileIdRef.current}/permissions`, (message) => {
          const data = JSON.parse(message.body)
          if (data.guestName !== guestNameRef.current) return

          if (data.canEdit) {
            setHasEditPermission(true)
            hasEditRef.current = true
            if (editorRef.current) {
              editorRef.current.updateOptions({ readOnly: false })
            }
            alert('🎉 You have been granted edit access!')
          } else {
            setHasEditPermission(false)
            hasEditRef.current = false
            if (editorRef.current) {
              editorRef.current.updateOptions({ readOnly: true })
            }
            alert('🔒 Edit permission has been revoked')
          }
        })

        // ── Active users ──────────────────────────────
        client.subscribe(`/topic/room/${fileIdRef.current}/users`, (message) => {
          const data = JSON.parse(message.body)
          const users = Array.from(data.users || [])
          cursorDecorations.current.forEach((_, username) => {
            if (!users.includes(username)) removeUserCursor(username)
          })
          setActiveUsers(users)
        })

        // ── Compiler events ───────────────────────────
        client.subscribe(`/topic/room/${fileIdRef.current}/compiler`, (message) => {
          const data = JSON.parse(message.body)
          handleCompilerEvent(data)
        })

        // ── Parser / syntax errors ────────────────────
        client.subscribe(`/topic/room/${fileIdRef.current}/errors`, (message) => {
          const data = JSON.parse(message.body)
          latestErrors.current = data.errors || []
          if (latestErrors.current.length > 0) {
            highlightSyntaxErrors(latestErrors.current)
          } else {
            clearSyntaxErrors()
          }
        })
      },

      onDisconnect: () => {
        console.log('❌ Guest WebSocket disconnected')
        setConnected(false)
      },
    })

    client.activate()
    stompRef.current = client
  }

  // ── Send edit (edit permission only) ────────────────

  function handleEditorChange(value) {
    if (isRemoteChange.current) {
      isRemoteChange.current = false
      return
    }
    if (!hasEditRef.current) return

    const newCode = value || ''
    setCode(newCode)
    codeRef.current = newCode

    if (stompRef.current?.connected) {
      stompRef.current.publish({
        destination: `/app/room/${fileIdRef.current}/edit`,
        body: JSON.stringify({ content: newCode, username: guestNameRef.current }),
      })
    }
  }

  // ── Cursor presence ─────────────────────────────────

  function getColorForUser(username) {
    if (!userColors.current.has(username)) {
      userColors.current.set(username, CURSOR_COLORS[colorIndex.current % CURSOR_COLORS.length])
      colorIndex.current++
    }
    return userColors.current.get(username)
  }

  function hexToRgba(hex, alpha) {
    const r = parseInt(hex.slice(1, 3), 16)
    const g = parseInt(hex.slice(3, 5), 16)
    const b = parseInt(hex.slice(5, 7), 16)
    return `rgba(${r},${g},${b},${alpha})`
  }

  function safeCssClass(username) {
    return username.replace(/[^a-zA-Z0-9]/g, '_')
  }

  function injectCursorStyle(username, color) {
    const safe = safeCssClass(username)
    const styleId = `cursor-style-${safe}`
    if (document.getElementById(styleId)) return
    const style = document.createElement('style')
    style.id = styleId
    style.textContent = `
      .cursor-bg-${safe} { background: ${hexToRgba(color, 0.12)} !important; }
      .cursor-label-${safe}::after {
        content: "${username}";
        background: ${color};
        color: #1e1e1e;
        font-size: 11px;
        font-family: 'Segoe UI', sans-serif;
        font-weight: 700;
        padding: 1px 5px;
        border-radius: 3px;
        position: absolute;
        margin-left: 2px;
        pointer-events: none;
        white-space: nowrap;
        z-index: 100;
        top: -1px;
        line-height: 1.4;
      }
    `
    document.head.appendChild(style)
  }

  function renderRemoteCursor(username, line, column) {
    const editor = editorRef.current
    if (!editor || !window.monaco) return
    const color      = getColorForUser(username)
    injectCursorStyle(username, color)
    const safe       = safeCssClass(username)
    const safeLine   = Math.max(1, line)
    const safeColumn = Math.max(1, column)
    const old        = cursorDecorations.current.get(username) || []
    const next = editor.deltaDecorations(old, [
      {
        range: new window.monaco.Range(safeLine, 1, safeLine, 1),
        options: { isWholeLine: true, className: `cursor-bg-${safe}`, zIndex: 1 },
      },
      {
        range: new window.monaco.Range(safeLine, safeColumn, safeLine, safeColumn),
        options: {
          afterContentClassName: `cursor-label-${safe}`,
          hoverMessage: { value: `**${username}** is here` },
          zIndex: 2,
        },
      },
    ])
    cursorDecorations.current.set(username, next)
  }

  function removeUserCursor(username) {
    const editor = editorRef.current
    if (!editor) return
    const old = cursorDecorations.current.get(username)
    if (old?.length > 0) editor.deltaDecorations(old, [])
    cursorDecorations.current.delete(username)
    userColors.current.delete(username)
    const safe = safeCssClass(username)
    document.getElementById(`cursor-style-${safe}`)?.remove()
  }

  function sendCursorPosition(position) {
    clearTimeout(cursorSendTimer.current)
    cursorSendTimer.current = setTimeout(() => {
      if (!stompRef.current?.connected) return
      stompRef.current.publish({
        destination: `/app/room/${fileIdRef.current}/cursor`,
        body: JSON.stringify({
          username: guestNameRef.current,
          line: position.lineNumber,
          column: position.column,
        }),
      })
    }, 50)
  }

  // ── Error highlighting ──────────────────────────────

  function highlightSyntaxErrors(errors) {
    const editor = editorRef.current
    if (!editor || !window.monaco) return
    const markers = errors.map(err => ({
      startLineNumber: err.line,
      startColumn: err.column || 1,
      endLineNumber: err.line,
      endColumn: (err.column || 1) + 10,
      message: err.message || 'Syntax error',
      severity: window.monaco.MarkerSeverity.Error,
    }))
    const model = editor.getModel()
    if (model) window.monaco.editor.setModelMarkers(model, 'parser', markers)
  }

  function clearSyntaxErrors() {
    const editor = editorRef.current
    if (!editor || !window.monaco) return
    const model = editor.getModel()
    if (model) window.monaco.editor.setModelMarkers(model, 'parser', [])
  }

  function handleEditorMount(editor, monaco) {
    editorRef.current = editor
    window.monaco = monaco
    editor.onDidChangeCursorPosition((e) => {
      sendCursorPosition(e.position)
    })
  }

  // ── Compiler events ─────────────────────────────────

  function handleCompilerEvent(data) {
    switch (data.event) {
      case 'compilation_started':
        setOutputColor('#0ff')
        setOutput(`🔨 Host is compiling ${data.className}...\n`)
        break
      case 'compilation_success':
        setOutputColor('#0f0')
        setOutput(prev => prev + `✅ Compilation successful (${data.compilationTimeMs}ms)\n🚀 Executing...\n`)
        break
      case 'compilation_failed':
        setOutputColor('#f44')
        setOutput('❌ COMPILATION FAILED\n\n' +
          (data.errorMessage ? data.errorMessage + '\n\n' : '') +
          (data.errors?.length > 0
            ? 'Errors:\n' + data.errors.map(e => `  Line ${e.lineNumber}: ${e.message}`).join('\n')
            : ''))
        break
      case 'execution_success':
        setOutputColor('#0f0')
        setOutput(`✅ CODE EXECUTED (${data.executionTimeMs}ms)\n\nOutput:\n` + (data.output || '(no output)'))
        break
      case 'execution_failed':
        setOutputColor('#f44')
        setOutput(`❌ EXECUTION FAILED\n\nError:\n${data.error || data.exceptionMessage || 'Unknown error'}\n\nExit code: ${data.exitCode}`)
        break
      default:
        break
    }
  }

  // ── Loading / error states ───────────────────────────

  if (error) return (
    <div style={styles.centeredMessage}>
      <span style={{ color: '#f44', fontSize: '1.2em' }}>❌ {error}</span>
    </div>
  )

  if (loading) return (
    <div style={styles.centeredMessage}>
      <span style={{ color: '#0f0', fontSize: '1.2em' }}>⏳ Joining room...</span>
    </div>
  )

  // ── Render ──────────────────────────────────────────

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
          ScriptDojo Live
        </h2>

        <div style={styles.infoBox}>
          <strong style={styles.infoLabel}>File:</strong> {fileName || 'Unknown'}
        </div>
        <div style={styles.infoBox}>
          <strong style={styles.infoLabel}>Guest:</strong> {guestName || 'Guest'}
        </div>

        <div style={{
          ...styles.permissionStatus,
          background: hasEditPermission ? '#0e639c' : '#3c3c3c',
          color: hasEditPermission ? '#fff' : '#888',
        }}>
          {hasEditPermission ? '✏️ Edit mode enabled' : '🔒 View-only mode'}
        </div>

        {/* ── Active Users ── */}
        <div style={styles.usersBox}>
          <h3 style={styles.usersTitle}>👥 Active Users</h3>
          {activeUsers.length === 0 ? (
            <div style={{ color: '#888', fontSize: '0.85em' }}>
              {connected ? 'Connecting...' : 'Disconnected'}
            </div>
          ) : (
            activeUsers.map(user => {
              const isMe     = user === guestName
              const dotColor = isMe ? '#888' : (userColors.current.get(user) || '#888')
              return (
                <div key={user} style={styles.userItem}>
                  <span style={styles.userName}>
                    <span style={{ ...styles.userDot, background: dotColor }} />
                    {user}{isMe ? ' (you)' : ''}
                  </span>
                </div>
              )
            })
          )}
        </div>

        {/* ── Console ── */}
        <div style={styles.consoleBox}>
          <h3 style={styles.consoleTitle}>📟 Console Output</h3>
          <pre style={{ ...styles.consoleOutput, color: outputColor }}>
            {output}
          </pre>
        </div>

      </div>

      {/* ── Editor ── */}
      <div style={styles.editorContainer}>
        <Editor
          height="100%"
          defaultLanguage="java"
          theme="vs-dark"
          value={code}
          onChange={handleEditorChange}
          onMount={handleEditorMount}
          options={{
            fontSize: 14,
            minimap: { enabled: true },
            automaticLayout: true,
            readOnly: !hasEditPermission,
          }}
        />
      </div>

    </div>
  )
}

const styles = {
  centeredMessage: {
    minHeight: '100vh',
    background: '#1e1e1e',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  container: {
    display: 'flex',
    height: '100vh',
    width: '100%',
    background: '#1e1e1e',
    color: '#d4d4d4',
    overflow: 'hidden',
  },
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
    color: '#f90',
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
  permissionStatus: {
    padding: '10px',
    borderRadius: '4px',
    textAlign: 'center',
    fontWeight: 'bold',
    fontSize: '0.9em',
    marginBottom: '4px',
  },
  usersBox: {
    padding: '10px',
    background: '#2d2d30',
    borderRadius: '4px',
  },
  usersTitle: {
    color: '#0ff',
    fontSize: '0.9em',
    marginBottom: '8px',
  },
  userItem: {
    display: 'flex',
    alignItems: 'center',
    padding: '5px 4px',
    borderRadius: '3px',
    marginBottom: '3px',
  },
  userName: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    fontSize: '0.85em',
    color: '#d4d4d4',
  },
  userDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    display: 'inline-block',
    flexShrink: 0,
  },
  consoleBox: {
    marginTop: '8px',
    padding: '10px',
    background: '#2d2d30',
    borderRadius: '4px',
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    minHeight: '150px',
  },
  consoleTitle: {
    color: '#0f0',
    fontSize: '0.9em',
    marginBottom: '8px',
  },
  consoleOutput: {
    flex: 1,
    background: '#000',
    padding: '10px',
    borderRadius: '4px',
    fontFamily: 'Courier New, monospace',
    fontSize: '0.8em',
    overflowY: 'auto',
    margin: 0,
    whiteSpace: 'pre-wrap',
    wordWrap: 'break-word',
    minHeight: '100px',
  },
  editorContainer: {
    flex: 1,
    overflow: 'hidden',
  },
}