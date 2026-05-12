import { useState, useEffect, useRef } from 'react'
import { useParams } from 'react-router-dom'
import Editor from '@monaco-editor/react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

/**
 * Guest editor page for ScriptDojo — the read-only (or edit-enabled) collaborative
 * view for unauthenticated participants who join via a shareable room URL.
 * Responsibilities:
 * - Fetches room data from GET /api/room/join/{roomId} on mount (no authentication required)
 * - Decodes the Base64-encoded file content and populates the Monaco Editor
 * - Establishes a STOMP/SockJS WebSocket connection, passing the guest's assigned
 *   display name as a custom STOMP header so the server can store it in the session
 * - Subscribes to six room channels:
 *     /topic/room/{fileId}              — inbound code edits from other participants
 *     /topic/room/{fileId}/cursors      — remote cursor position updates
 *     /topic/room/{fileId}/permissions  — host-granted or host-revoked edit access
 *     /topic/room/{fileId}/users        — active user presence list
 *     /topic/room/{fileId}/compiler     — compiler pipeline stage events
 *     /topic/room/{fileId}/errors       — ANTLR syntax error broadcasts
 * - Starts in read-only mode; switches to editable when the host grants edit permission
 * - Broadcasts local edits and cursor moves only when edit permission is active
 * The roomId path parameter (/room/:roomId) is used to fetch all session data.
 * No login or account is required — the guest name is assigned by the server.
 */
export default function GuestPage() {
  const { roomId } = useParams()

  const [fileId, setFileId]                       = useState(null)
  const [fileName, setFileName]                   = useState('')
  const [guestName, setGuestName]                 = useState('')
  const [code, setCode]                           = useState('// Loading...')
  const [connected, setConnected]                 = useState(false)
  const [hasEditPermission, setHasEditPermission] = useState(false)  // Starts false — view-only
  const [activeUsers, setActiveUsers]             = useState([])
  const [output, setOutput]                       = useState('Waiting for host to run code...')
  const [outputColor, setOutputColor]             = useState('#0f0')
  const [loading, setLoading]                     = useState(true)
  const [error, setError]                         = useState('')

  // Ref to the Monaco editor instance — used for marker, decoration, and readOnly APIs
  const editorRef      = useRef(null)

  // Ref to the STOMP client — used to publish messages and deactivate on unmount
  const stompRef       = useRef(null)

  // Flag that prevents an inbound remote edit from being re-broadcast as a local change
  const isRemoteChange = useRef(false)

  // Stores the most recent syntax error list so markers can be re-applied after
  // the editor content is replaced by a remote edit
  const latestErrors   = useRef([])

  // Ref mirrors of state values so WebSocket callbacks always read the latest values
  // without capturing stale closures
  const codeRef        = useRef('')
  const hasEditRef     = useRef(false)   // Mirror of hasEditPermission
  const guestNameRef   = useRef('')      // Mirror of guestName
  const fileIdRef      = useRef(null)    // Mirror of fileId

  // ─ Cursor presence refs 
  const cursorDecorations = useRef(new Map())  // username → Monaco decoration IDs
  const userColors        = useRef(new Map())  // username → assigned hex colour
  const colorIndex        = useRef(0)          // Index into CURSOR_COLORS for next assignment
  const cursorSendTimer   = useRef(null)       // Debounce timer for cursor position sends

  /**
   * Colour palette cycled through for remote cursor overlays.
   * Each new remote user is assigned the next colour in the array.
   */
  const CURSOR_COLORS = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
    '#DDA0DD', '#98D8C8', '#F39C12', '#A29BFE', '#FD79A8',
  ]

  // ─ Fetch room data on mount 

  /**
   * Fetches room data from the public join endpoint on mount.
   * Decodes the Base64-encoded file content and populates all state and ref values
   * needed before the WebSocket connection is established.
   * Sets an error message if the room does not exist or the request fails.
   */
  useEffect(() => {
    if (!roomId) { setError('No room ID provided'); return }

    fetch(`/api/room/join/${roomId}`)
      .then(res => {
        if (!res.ok) throw new Error('Room not found')
        return res.json()
      })
      .then(data => {
        // Decode the Base64-encoded file content supplied by RoomController
        const decoded = atob(data.content)
        setFileId(data.fileId)
        setFileName(data.fileName)
        setGuestName(data.guestName)
        setCode(decoded)
        // Populate refs immediately so the WebSocket connection callback
        // can access these values before the next React render cycle
        fileIdRef.current    = data.fileId
        guestNameRef.current = data.guestName
        codeRef.current      = decoded
        setLoading(false)
      })
      .catch(() => {
        setError('Room not found or has expired.')
        setLoading(false)
      })
  }, [roomId])

  // ─ WebSocket connection 

  // Connect only after both fileId and guestName are available from the join response
  useEffect(() => {
    if (!fileId || !guestName) return
    connectWebSocket()
    return () => {
      if (stompRef.current) stompRef.current.deactivate()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fileId, guestName])

  /**
   * Creates and activates a STOMP client over SockJS and subscribes to all room channels.
   * Passes the guest's display name as a custom STOMP connect header so the server's
   * WebSocketConfig interceptor can store it in the session for use by message handlers.
   */
  function connectWebSocket() {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      // Guest username is sent as a custom STOMP header on CONNECT so the server
      // can identify the guest without a Spring Security Principal
      connectHeaders: { username: guestNameRef.current },
      reconnectDelay: 5000,

      onConnect: () => {
        console.log('✅ Guest WebSocket connected as:', guestNameRef.current)
        setConnected(true)

        // ─ Inbound edits 
        // Ignore edits from this guest to prevent echo-back loops.
        // Re-apply syntax markers after remote edits since replacing the editor
        // value clears all existing decorations.
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

        // ─ Remote cursor positions 
        // Ignore cursor events from this guest — only render other participants
        client.subscribe(`/topic/room/${fileIdRef.current}/cursors`, (message) => {
          const data = JSON.parse(message.body)
          if (data.username === guestNameRef.current) return
          renderRemoteCursor(data.username, data.line, data.column)
        })

        // ─ Permission updates 
        // Filter to only process permission events targeted at this guest.
        // Enables or disables the Monaco editor's readOnly option immediately
        // and alerts the guest so they are aware of the change.
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

        // ─ Active user presence 
        // Remove cursor decorations for users who have disconnected since
        // the last broadcast, then update the active users list in state
        client.subscribe(`/topic/room/${fileIdRef.current}/users`, (message) => {
          const data = JSON.parse(message.body)
          const users = Array.from(data.users || [])
          cursorDecorations.current.forEach((_, username) => {
            if (!users.includes(username)) removeUserCursor(username)
          })
          setActiveUsers(users)
        })

        // ─ Compiler pipeline events 
        client.subscribe(`/topic/room/${fileIdRef.current}/compiler`, (message) => {
          const data = JSON.parse(message.body)
          handleCompilerEvent(data)
        })

        // ─ ANTLR syntax error broadcasts 
        // Cache the latest errors in a ref so they can be re-applied after
        // remote edits replace the editor content and clear markers
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

  // ─ Outbound edit broadcast 

  /**
   * Called by Monaco on every keystroke. Skips remote changes to prevent echo-back
   * and silently drops local edits when the guest does not have edit permission.
   * Publishes the full current content to /app/room/{fileId}/edit when permitted.
   * @param {string} value - the current full content of the Monaco editor
   */
  function handleEditorChange(value) {
    if (isRemoteChange.current) {
      isRemoteChange.current = false
      return
    }
    // Guests without edit permission cannot publish edits — the editor is also
    // set to readOnly but this guard provides a secondary safety check
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

  // ─ Cursor presence 

  /** Returns a consistent hex colour for the given username, assigning a new one if needed. */
  function getColorForUser(username) {
    if (!userColors.current.has(username)) {
      userColors.current.set(username, CURSOR_COLORS[colorIndex.current % CURSOR_COLORS.length])
      colorIndex.current++
    }
    return userColors.current.get(username)
  }

  /** Converts a hex colour string to an rgba() value with the given alpha. */
  function hexToRgba(hex, alpha) {
    const r = parseInt(hex.slice(1, 3), 16)
    const g = parseInt(hex.slice(3, 5), 16)
    const b = parseInt(hex.slice(5, 7), 16)
    return `rgba(${r},${g},${b},${alpha})`
  }

  /**
   * Sanitises a username for use as a CSS class name by replacing any
   * non-alphanumeric character with an underscore.
   */
  function safeCssClass(username) {
    return username.replace(/[^a-zA-Z0-9]/g, '_')
  }

  /**
   * Injects a <style> element for the given user's cursor overlay classes.
   * Idempotent — skips injection if the element already exists.
   */
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

  /**
   * Renders or updates a remote user's cursor overlay using Monaco's deltaDecorations API.
   * Applies a whole-line background highlight and a username label at the cursor column.
   * @param {string} username - the remote user's display name
   * @param {number} line     - the 1-based line number of their cursor
   * @param {number} column   - the 1-based column of their cursor
   */
  function renderRemoteCursor(username, line, column) {
    const editor = editorRef.current
    if (!editor || !window.monaco) return
    const color      = getColorForUser(username)
    injectCursorStyle(username, color)
    const safe       = safeCssClass(username)
    const safeLine   = Math.max(1, line)    // Clamp to valid range
    const safeColumn = Math.max(1, column)
    const old        = cursorDecorations.current.get(username) || []
    const next = editor.deltaDecorations(old, [
      {
        // Whole-line background tint
        range: new window.monaco.Range(safeLine, 1, safeLine, 1),
        options: { isWholeLine: true, className: `cursor-bg-${safe}`, zIndex: 1 },
      },
      {
        // Username label at the cursor column position
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

  /**
   * Removes all Monaco decorations and injected styles for a disconnected user.
   * Cleans up cursorDecorations and userColors maps to release memory.
   * @param {string} username - the departing user's display name
   */
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

  /**
   * Debounces cursor position broadcasts to /app/room/{fileId}/cursor.
   * A 50ms delay avoids flooding the server on rapid cursor moves.
   * @param {{ lineNumber: number, column: number }} position - Monaco cursor position
   */
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

  // ─ Syntax error highlighting 

  /**
   * Applies Monaco editor markers for the given ANTLR syntax errors.
   * Markers appear as red squiggles under the offending tokens.
   * @param {Array} errors - array of SyntaxError DTOs from the parser broadcast
   */
  function highlightSyntaxErrors(errors) {
    const editor = editorRef.current
    if (!editor || !window.monaco) return
    const markers = errors.map(err => ({
      startLineNumber: err.line,
      startColumn:     err.column || 1,
      endLineNumber:   err.line,
      endColumn:       (err.column || 1) + 10,
      message:         err.message || 'Syntax error',
      severity:        window.monaco.MarkerSeverity.Error,
    }))
    const model = editor.getModel()
    if (model) window.monaco.editor.setModelMarkers(model, 'parser', markers)
  }

  /**
   * Clears all ANTLR-sourced markers from the Monaco editor model.
   * Called when the parser broadcasts an empty error list.
   */
  function clearSyntaxErrors() {
    const editor = editorRef.current
    if (!editor || !window.monaco) return
    const model = editor.getModel()
    if (model) window.monaco.editor.setModelMarkers(model, 'parser', [])
  }

  /**
   * Called by the Monaco Editor component when the editor is ready.
   * Stores the editor instance and exposes monaco on window so WebSocket callbacks
   * can access the marker and decoration APIs outside the React component scope.
   */
  function handleEditorMount(editor, monaco) {
    editorRef.current = editor
    window.monaco = monaco
    editor.onDidChangeCursorPosition((e) => {
      sendCursorPosition(e.position)
    })
  }

  // ─ Compiler event handler

  /**
   * Handles compiler pipeline stage events broadcast to /topic/room/{fileId}/compiler.
   * Updates the console output and colour to reflect the current pipeline state.
   * Phrased from the guest's perspective (e.g. "Host is compiling...").
   * @param {{ event: string }} data - compiler event payload from the WebSocket broadcast
   */
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

  // ─ Loading / error states

  // Show a full-screen error message if the room fetch failed
  if (error) return (
    <div style={styles.centeredMessage}>
      <span style={{ color: '#f44', fontSize: '1.2em' }}>❌ {error}</span>
    </div>
  )

  // Show a full-screen loading indicator while the join request is in flight
  if (loading) return (
    <div style={styles.centeredMessage}>
      <span style={{ color: '#0f0', fontSize: '1.2em' }}>⏳ Joining room...</span>
    </div>
  )

  // ─ Render 

  return (
    <div style={styles.container}>

      {/* ── Sidebar ── */}
      <div style={styles.sidebar}>
        {/* Title with live WebSocket connection status indicator */}
        <h2 style={styles.sidebarTitle}>
          <span style={{
            ...styles.statusDot,
            background: connected ? '#0f0' : '#f44',
            boxShadow:  connected ? '0 0 8px #0f0' : '0 0 8px #f44',
          }} />
          ScriptDojo Live
        </h2>

        <div style={styles.infoBox}>
          <strong style={styles.infoLabel}>File:</strong> {fileName || 'Unknown'}
        </div>
        <div style={styles.infoBox}>
          <strong style={styles.infoLabel}>Guest:</strong> {guestName || 'Guest'}
        </div>

        {/* Permission status badge — updates in real time via the permissions channel */}
        <div style={{
          ...styles.permissionStatus,
          background: hasEditPermission ? '#0e639c' : '#3c3c3c',
          color:      hasEditPermission ? '#fff'    : '#888',
        }}>
          {hasEditPermission ? '✏️ Edit mode enabled' : '🔒 View-only mode'}
        </div>

        {/* Active Users */}
        <div style={styles.usersBox}>
          <h3 style={styles.usersTitle}>👥 Active Users</h3>
          {activeUsers.length === 0 ? (
            <div style={{ color: '#888', fontSize: '0.85em' }}>
              {connected ? 'Connecting...' : 'Disconnected'}
            </div>
          ) : (
            activeUsers.map(user => {
              const isMe     = user === guestName
              // Use the assigned cursor colour for remote users; grey for self
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

        {/* Console output — shows compiler events broadcast by the host */}
        <div style={styles.consoleBox}>
          <h3 style={styles.consoleTitle}>📟 Console Output</h3>
          <pre style={{ ...styles.consoleOutput, color: outputColor }}>
            {output}
          </pre>
        </div>
      </div>

      {/* Editor — readOnly driven by hasEditPermission */}
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
            // readOnly is set here on initial render; permission changes update it
            // via editorRef.current.updateOptions() in the permissions subscription
            readOnly: !hasEditPermission,
          }}
        />
      </div>

    </div>
  )
}

// ─ Inline styles
// Defined outside the component to prevent object recreation on every render.
// Follows the VS Code dark theme palette; sidebar title uses orange (#f90) to
// visually distinguish the guest view from the host editor (#0f0).
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
    color: '#f90',  // Orange distinguishes the guest view from the host's green title
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
  infoLabel: { color: '#0ff' },
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
  editorContainer: { flex: 1, overflow: 'hidden' },
}