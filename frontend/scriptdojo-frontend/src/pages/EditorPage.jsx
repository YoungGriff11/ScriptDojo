import { useState, useEffect, useRef, useCallback } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import Editor from "@monaco-editor/react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

export default function EditorPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const fileId = searchParams.get("fileId");

  const [fileName, setFileName] = useState("Loading...");
  const [code, setCode] = useState("// Loading...");
  const [currentUser, setCurrentUser] = useState("");
  const [connected, setConnected] = useState(false);
  const [output, setOutput] = useState("Ready to run Java code...");
  const [outputColor, setOutputColor] = useState("#0f0");
  const [shareUrl, setShareUrl] = useState("");
  const [showShareLink, setShowShareLink] = useState(false);
  const [activeUsers, setActiveUsers] = useState([]);

  const editorRef = useRef(null);
  const stompRef = useRef(null);
  const isRemoteChange = useRef(false);
  const latestErrors = useRef([]);
  const currentUserRef = useRef("");
  const codeRef = useRef(code);

  // Cursor presence state
  const cursorDecorations = useRef(new Map());
  const userColors = useRef(new Map());
  const colorIndex = useRef(0);
  const cursorSendTimer = useRef(null);

  const CURSOR_COLORS = [
    "#FF6B6B",
    "#4ECDC4",
    "#45B7D1",
    "#96CEB4",
    "#FFEAA7",
    "#DDA0DD",
    "#98D8C8",
    "#F39C12",
    "#A29BFE",
    "#FD79A8",
  ];

  // Keep codeRef in sync with code state
  useEffect(() => {
    codeRef.current = code;
  }, [code]);

  // ── Load user + file ────────────────────────────────

  async function loadCurrentUser() {
    try {
      const res = await fetch("/api/user/me", { credentials: "include" });
      if (res.status === 401) {
        navigate("/login");
        return;
      }
      const user = await res.json();
      setCurrentUser(user.username);
      currentUserRef.current = user.username;
    } catch {
      navigate("/login");
    }
  }

  async function loadFile() {
    try {
      const res = await fetch(`/api/files/${fileId}`, {
        credentials: "include",
      });
      if (res.status === 401) {
        navigate("/login");
        return;
      }
      const data = await res.json();
      setFileName(data.name);
      setCode(data.content);
      codeRef.current = data.content;
    } catch {
      setCode("// Error loading file");
    }
  }

  useEffect(() => {
    if (!fileId) {
      navigate("/dashboard");
      return;
    }
    loadCurrentUser();
    loadFile();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fileId]);

  // ── Connect WebSocket after user is loaded ──────────

  useEffect(() => {
    if (!currentUser || !fileId) return;
    connectWebSocket();
    return () => {
      if (stompRef.current) stompRef.current.deactivate();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser, fileId]);

  function connectWebSocket() {
    const client = new Client({
      webSocketFactory: () => new SockJS("/ws"),
      reconnectDelay: 5000,

      onConnect: () => {
        console.log("✅ WebSocket connected");
        setConnected(true);

        // ── Room edits ────────────────────────────────
        client.subscribe(`/topic/room/${fileId}`, (message) => {
          const data = JSON.parse(message.body);
          if (data.username !== currentUserRef.current) {
            isRemoteChange.current = true;
            setCode(data.content);
            codeRef.current = data.content;
            setTimeout(() => {
              if (latestErrors.current.length > 0) {
                highlightSyntaxErrors(latestErrors.current);
              } else {
                clearSyntaxErrors();
              }
            }, 50);
          }
        });

        // ── Cursor positions ──────────────────────────
        client.subscribe(`/topic/room/${fileId}/cursors`, (message) => {
          const data = JSON.parse(message.body);
          if (data.username === currentUserRef.current) return;
          renderRemoteCursor(data.username, data.line, data.column);
        });

        // ── Active users ──────────────────────────────
        client.subscribe(`/topic/room/${fileId}/users`, (message) => {
          const data = JSON.parse(message.body);
          const users = Array.from(data.users || []);

          // Remove cursors for users who have left
          cursorDecorations.current.forEach((_, username) => {
            if (!users.includes(username)) removeUserCursor(username);
          });

          setActiveUsers(users);
        });

        // ── Compiler events ───────────────────────────
        client.subscribe(`/topic/room/${fileId}/compiler`, (message) => {
          const data = JSON.parse(message.body);
          handleCompilerEvent(data);
        });

        // ── Parser / syntax errors ────────────────────
        client.subscribe(`/topic/room/${fileId}/errors`, (message) => {
          const data = JSON.parse(message.body);
          latestErrors.current = data.errors || [];
          if (latestErrors.current.length > 0) {
            highlightSyntaxErrors(latestErrors.current);
          } else {
            clearSyntaxErrors();
          }
        });
      },

      onDisconnect: () => {
        console.log("❌ WebSocket disconnected");
        setConnected(false);
      },
    });

    client.activate();
    stompRef.current = client;
  }

  // ── Send edit over WebSocket ────────────────────────

  function handleEditorChange(value) {
    if (isRemoteChange.current) {
      isRemoteChange.current = false;
      return;
    }
    const newCode = value || "";
    setCode(newCode);
    codeRef.current = newCode;

    if (stompRef.current?.connected) {
      stompRef.current.publish({
        destination: `/app/room/${fileId}/edit`,
        body: JSON.stringify({
          content: newCode,
          username: currentUserRef.current,
        }),
      });
    }
  }

  // ── Cursor presence ─────────────────────────────────

  function getColorForUser(username) {
    if (!userColors.current.has(username)) {
      userColors.current.set(
        username,
        CURSOR_COLORS[colorIndex.current % CURSOR_COLORS.length],
      );
      colorIndex.current++;
    }
    return userColors.current.get(username);
  }

  function hexToRgba(hex, alpha) {
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return `rgba(${r},${g},${b},${alpha})`;
  }

  function safeCssClass(username) {
    return username.replace(/[^a-zA-Z0-9]/g, "_");
  }

  function injectCursorStyle(username, color) {
    const safe = safeCssClass(username);
    const styleId = `cursor-style-${safe}`;
    if (document.getElementById(styleId)) return;
    const style = document.createElement("style");
    style.id = styleId;
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
    `;
    document.head.appendChild(style);
  }

  function renderRemoteCursor(username, line, column) {
    const editor = editorRef.current;
    if (!editor) return;
    const monaco = window.monaco;
    if (!monaco) return;

    const color = getColorForUser(username);
    injectCursorStyle(username, color);

    const safe = safeCssClass(username);
    const safeLine = Math.max(1, line);
    const safeColumn = Math.max(1, column);
    const old = cursorDecorations.current.get(username) || [];

    const next = editor.deltaDecorations(old, [
      {
        range: new monaco.Range(safeLine, 1, safeLine, 1),
        options: {
          isWholeLine: true,
          className: `cursor-bg-${safe}`,
          zIndex: 1,
        },
      },
      {
        range: new monaco.Range(safeLine, safeColumn, safeLine, safeColumn),
        options: {
          afterContentClassName: `cursor-label-${safe}`,
          hoverMessage: { value: `**${username}** is here` },
          zIndex: 2,
        },
      },
    ]);

    cursorDecorations.current.set(username, next);
  }

  function removeUserCursor(username) {
    const editor = editorRef.current;
    if (!editor) return;
    const old = cursorDecorations.current.get(username);
    if (old?.length > 0) editor.deltaDecorations(old, []);
    cursorDecorations.current.delete(username);
    userColors.current.delete(username);
    const safe = safeCssClass(username);
    document.getElementById(`cursor-style-${safe}`)?.remove();
  }

  function sendCursorPosition(position) {
    clearTimeout(cursorSendTimer.current);
    cursorSendTimer.current = setTimeout(() => {
      if (!stompRef.current?.connected) return;
      stompRef.current.publish({
        destination: `/app/room/${fileId}/cursor`,
        body: JSON.stringify({
          username: currentUserRef.current,
          line: position.lineNumber,
          column: position.column,
        }),
      });
    }, 50);
  }

  // ── Error highlighting ──────────────────────────────

  function highlightSyntaxErrors(errors) {
    const editor = editorRef.current;
    if (!editor || !window.monaco) return;
    const markers = errors.map((err) => ({
      startLineNumber: err.line,
      startColumn: err.column || 1,
      endLineNumber: err.line,
      endColumn: (err.column || 1) + 10,
      message: err.message || "Syntax error",
      severity: window.monaco.MarkerSeverity.Error,
    }));
    const model = editor.getModel();
    if (model) window.monaco.editor.setModelMarkers(model, "parser", markers);
  }

  function clearSyntaxErrors() {
    const editor = editorRef.current;
    if (!editor || !window.monaco) return;
    const model = editor.getModel();
    if (model) window.monaco.editor.setModelMarkers(model, "parser", []);
  }

  // ── Monaco mount ────────────────────────────────────

  function handleEditorMount(editor, monaco) {
    editorRef.current = editor;
    window.monaco = monaco;

    // Send cursor position on move
    editor.onDidChangeCursorPosition((e) => {
      sendCursorPosition(e.position);
    });
  }

  // ── Compiler events from WebSocket ──────────────────

  function handleCompilerEvent(data) {
    switch (data.event) {
      case "compilation_started":
        setOutputColor("#0ff");
        setOutput(`🔨 Compiling ${data.className}...\n`);
        break;
      case "compilation_success":
        setOutputColor("#0f0");
        setOutput(
          (prev) =>
            prev +
            `✅ Compilation successful (${data.compilationTimeMs}ms)\n🚀 Executing...\n`,
        );
        break;
      case "compilation_failed":
        setOutputColor("#f44");
        setOutput(
          "❌ COMPILATION FAILED\n\n" +
            (data.errorMessage ? data.errorMessage + "\n\n" : "") +
            (data.errors?.length > 0
              ? "Errors:\n" +
                data.errors
                  .map((e) => `  Line ${e.lineNumber}: ${e.message}`)
                  .join("\n")
              : ""),
        );
        break;
      case "execution_success":
        setOutputColor("#0f0");
        setOutput(
          `✅ EXECUTION SUCCESSFUL (${data.executionTimeMs}ms)\n\nOutput:\n` +
            (data.output || "(no output)"),
        );
        break;
      case "execution_failed":
        setOutputColor("#f44");
        setOutput(
          `❌ EXECUTION FAILED\n\nError:\n${data.error || data.exceptionMessage || "Unknown error"}\n\nExit code: ${data.exitCode}`,
        );
        break;
      default:
        break;
    }
  }

  // ── Save ────────────────────────────────────────────

  async function saveFile() {
    try {
      const res = await fetch(`/api/files/${fileId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ content: codeRef.current }),
      });
      if (res.ok) alert("✅ File saved successfully!");
      else alert("❌ Failed to save file");
    } catch {
      alert("❌ Network error saving file");
    }
  }

  // ── Run ─────────────────────────────────────────────

  async function runCode() {
    setOutput("⏳ Compiling and running...");
    setOutputColor("#0ff");
    try {
      const res = await fetch("/api/compiler/run", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ code: codeRef.current, fileId }),
      });
      const data = await res.json();
      if (data.success) {
        setOutputColor("#0f0");
        setOutput(
          `✅ EXECUTION SUCCESSFUL (${data.executionResult.executionTimeMs}ms)\n\nOutput:\n` +
            (data.executionResult.output || "(no output)"),
        );
      } else if (data.stage === "compilation") {
        displayCompilationErrors(data.compilationResult);
      } else {
        displayExecutionError(data.executionResult);
      }
    } catch {
      setOutputColor("#f44");
      setOutput("❌ Network error running code");
    }
  }

  function displayCompilationErrors(result) {
    setOutputColor("#f44");
    let text = "❌ COMPILATION FAILED\n\n";
    if (result.errorMessage) text += result.errorMessage + "\n\n";
    if (result.errors?.length > 0) {
      text += "Errors:\n";
      result.errors.forEach((e) => {
        text += `  Line ${e.lineNumber}: ${e.message}\n`;
      });
    }
    setOutput(text);
  }

  function displayExecutionError(result) {
    setOutputColor("#f44");
    setOutput(
      `❌ EXECUTION FAILED\n\nError:\n${result.error || result.exceptionMessage || "Unknown error"}\n\nExit code: ${result.exitCode}`,
    );
  }

  // ── Share link ──────────────────────────────────────

  async function generateShareLink() {
    try {
      const res = await fetch(`/api/room/create?fileId=${fileId}`, {
        method: "POST",
        credentials: "include",
      });
      const data = await res.json();
      if (data.url) {
        setShareUrl(data.url);
        setShowShareLink(true);
      } else {
        alert("❌ Failed to generate share link");
      }
    } catch {
      alert("❌ Network error generating share link");
    }
  }
  async function grantEditPermission(guestName) {
    try {
      const res = await fetch(
        `/api/permissions/grant-edit?fileId=${fileId}&guestName=${encodeURIComponent(guestName)}`,
        { method: "POST", credentials: "include" },
      );
      if (res.ok) {
        alert(`✅ Edit permission granted to ${guestName}`);
      } else {
        alert("❌ Failed to grant permission");
      }
    } catch {
      alert("❌ Network error granting permission");
    }
  }

  function copyShareLink() {
    navigator.clipboard
      .writeText(shareUrl)
      .then(() => alert("✅ Link copied to clipboard!"))
      .catch(() => alert("❌ Failed to copy link"));
  }

  // ── Render ──────────────────────────────────────────

  return (
    <div style={styles.container}>
      {/* ── Sidebar ── */}
      <div style={styles.sidebar}>
        <h2 style={styles.sidebarTitle}>
          <span
            style={{
              ...styles.statusDot,
              background: connected ? "#0f0" : "#f44",
              boxShadow: connected ? "0 0 8px #0f0" : "0 0 8px #f44",
            }}
          />
          ScriptDojo
        </h2>

        <div style={styles.infoBox}>
          <strong style={styles.infoLabel}>File:</strong> {fileName}
        </div>
        <div style={styles.infoBox}>
          <strong style={styles.infoLabel}>Owner:</strong>{" "}
          {currentUser || "You"}
        </div>

        <button style={styles.btn} onClick={saveFile}>
          💾 Save File
        </button>
        <button style={styles.btn} onClick={runCode}>
          ▶️ Run Java Code
        </button>
        <button style={styles.btn} onClick={generateShareLink}>
          🔗 Generate Share Link
        </button>

        {showShareLink && (
          <div style={styles.shareLinkBox}>
            <h3 style={styles.shareLinkTitle}>Share Link</h3>
            <input
              style={styles.shareLinkInput}
              type="text"
              value={shareUrl}
              readOnly
            />
            <button style={styles.btn} onClick={copyShareLink}>
              📋 Copy Link
            </button>
          </div>
        )}

        {/* ── Active Users ── */}
        <div style={styles.usersBox}>
          <h3 style={styles.usersTitle}>👥 Active Users</h3>
          {activeUsers.length === 0 ? (
            <div style={{ color: "#888", fontSize: "0.85em" }}>
              {connected ? "Only you" : "Connecting..."}
            </div>
          ) : (
            activeUsers.map((user) => {
              const isMe = user === currentUser;
              const isGuest = user.startsWith("Guest");
              const dotColor = isMe
                ? "#888"
                : userColors.current.get(user) || "#888";
              return (
                <div key={user} style={styles.userItem}>
                  <span style={styles.userName}>
                    <span style={{ ...styles.userDot, background: dotColor }} />
                    {user}
                    {isMe ? " (you)" : ""}
                  </span>
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "5px",
                    }}
                  >
                    <span
                      style={{
                        ...styles.permissionBadge,
                        background: isGuest ? "#3c3c3c" : "#0e639c",
                        color: isGuest ? "#888" : "#fff",
                      }}
                    >
                      {isGuest ? "VIEW" : "EDIT"}
                    </span>
                    {isGuest && !isMe && (
                      <button
                        style={styles.grantBtn}
                        onClick={() => grantEditPermission(user)}
                      >
                        Grant Edit
                      </button>
                    )}
                  </div>
                </div>
              );
            })
          )}
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
            onChange={handleEditorChange}
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
              onClick={() => {
                setOutput("Ready to run Java code...");
                setOutputColor("#0f0");
              }}
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
  );
}

const styles = {
  grantBtn: {
  padding: '2px 8px',
  background: '#0f0',
  color: '#000',
  border: 'none',
  borderRadius: '3px',
  cursor: 'pointer',
  fontSize: '10px',
  fontWeight: 'bold',
},
  container: {
    display: "flex",
    height: "100vh",
    width: "100%",
    background: "#1e1e1e",
    color: "#d4d4d4",
    overflow: "hidden",
  },
  sidebar: {
    width: "280px",
    minWidth: "280px",
    background: "#252526",
    borderRight: "1px solid #3c3c3c",
    padding: "20px",
    overflowY: "auto",
    display: "flex",
    flexDirection: "column",
    gap: "8px",
  },
  sidebarTitle: {
    color: "#0f0",
    marginBottom: "12px",
    display: "flex",
    alignItems: "center",
    gap: "10px",
    fontSize: "1.2em",
  },
  statusDot: {
    width: "10px",
    height: "10px",
    borderRadius: "50%",
    display: "inline-block",
    flexShrink: 0,
  },
  infoBox: {
    padding: "10px",
    background: "#2d2d30",
    borderRadius: "4px",
    fontSize: "0.9em",
    marginBottom: "4px",
  },
  infoLabel: {
    color: "#0ff",
  },
  btn: {
    width: "100%",
    padding: "11px",
    background: "#0e639c",
    color: "white",
    border: "none",
    borderRadius: "4px",
    cursor: "pointer",
    fontSize: "14px",
    fontWeight: "bold",
    textAlign: "left",
  },
  shareLinkBox: {
    padding: "10px",
    background: "#2d2d30",
    borderRadius: "4px",
    marginTop: "4px",
  },
  shareLinkTitle: {
    color: "#0ff",
    fontSize: "0.9em",
    marginBottom: "8px",
  },
  shareLinkInput: {
    width: "100%",
    padding: "7px",
    background: "#1e1e1e",
    border: "1px solid #3c3c3c",
    color: "#d4d4d4",
    borderRadius: "3px",
    fontFamily: "monospace",
    fontSize: "11px",
    marginBottom: "6px",
    boxSizing: "border-box",
  },
  usersBox: {
    marginTop: "12px",
    padding: "10px",
    background: "#2d2d30",
    borderRadius: "4px",
  },
  usersTitle: {
    color: "#0ff",
    fontSize: "0.9em",
    marginBottom: "8px",
  },
  userItem: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    padding: "6px 4px",
    borderRadius: "3px",
    marginBottom: "4px",
  },
  userName: {
    display: "flex",
    alignItems: "center",
    gap: "6px",
    fontSize: "0.85em",
    color: "#d4d4d4",
  },
  userDot: {
    width: "8px",
    height: "8px",
    borderRadius: "50%",
    display: "inline-block",
    flexShrink: 0,
  },
  permissionBadge: {
    padding: "2px 7px",
    borderRadius: "3px",
    fontSize: "10px",
    fontWeight: "bold",
  },
  editorContainer: {
    flex: 1,
    display: "flex",
    flexDirection: "column",
    overflow: "hidden",
  },
  editorWrapper: {
    flex: 1,
    overflow: "hidden",
  },
  consoleContainer: {
    height: "220px",
    minHeight: "220px",
    background: "#1e1e1e",
    borderTop: "1px solid #3c3c3c",
    display: "flex",
    flexDirection: "column",
  },
  consoleHeader: {
    background: "#2d2d30",
    padding: "8px 15px",
    borderBottom: "1px solid #3c3c3c",
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
  },
  consoleTitle: {
    color: "#0f0",
    fontSize: "13px",
    margin: 0,
  },
  clearBtn: {
    padding: "4px 12px",
    background: "#3c3c3c",
    color: "#d4d4d4",
    border: "none",
    borderRadius: "3px",
    cursor: "pointer",
    fontSize: "12px",
  },
  consoleOutput: {
    flex: 1,
    background: "#000",
    padding: "12px 15px",
    fontFamily: "Courier New, monospace",
    fontSize: "0.85em",
    overflowY: "auto",
    margin: 0,
    whiteSpace: "pre-wrap",
    wordWrap: "break-word",
  },
};
