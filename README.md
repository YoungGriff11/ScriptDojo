ScriptDojo 🖥️

Real-Time Collaborative Java IDE — Like Google Docs, but for code.

ScriptDojo is a web-based collaborative coding platform designed for educational and professional assessment environments. Lecturers, interviewers, and group leaders can share a single URL with any number of participants who join instantly — no account required — and collaborate on Java code in real time.

✨ Features

Real-time collaborative editing — Every keystroke is broadcast to all connected participants with sub-200ms latency
Cursor presence — Coloured cursors with name labels show exactly where each participant is typing
Frictionless guest access — Share a single link; guests join with no account, no installation, no Git required
Host-controlled permissions — Grant or revoke individual edit access with one click during a live session
Live Java compilation — Compile and run code with one click; identical console output appears on all screens simultaneously
ANTLR v4 syntax highlighting — Real-time Java syntax error detection with squiggle underlining in the editor
Monaco Editor — The same editor engine that powers VS Code
Docker Compose deployment — One command starts the entire application


🛠️ Tech Stack
Backend

Java 21 + Spring Boot 3.3
Spring Security — Form-based authentication with BCrypt password encoding
Spring WebSocket + STOMP over SockJS — Real-time messaging
Spring Data JPA + Hibernate — MySQL persistence
javax.tools.JavaCompiler — In-process Java compilation (requires JDK, not JRE)
ANTLR v4 — Java syntax parsing and error detection
Lombok — Boilerplate reduction

Frontend

React 18 + Vite — Single-page application
Monaco Editor — VS Code editor core
@stomp/stompjs + SockJS — WebSocket client
React Router v6 — Client-side routing
Axios — HTTP client

Database

MySQL 8 — Relational storage for users, files, rooms, and permissions

Deployment

Docker + Docker Compose — Two-container deployment (Spring Boot + MySQL)
Hosted on Railway at scriptdojo.ie


🚀 Running Locally with Docker
Prerequisites

Docker Desktop installed and running

Steps
1. Clone the repository
   bashgit clone https://gitlab.com/YoungGriff11/scriptdojo.git
   cd scriptdojo
2. Start the application
   bashdocker-compose up --build
   This single command:

Builds the React frontend (npm run build)
Compiles the Spring Boot JAR with the React build inside it
Starts the MySQL container
Starts the Spring Boot container
The app is ready when you see: Started BackendApplication

3. Open the app
   http://localhost:8080
4. Stop the application
   bash# Stop containers (keeps database data)
   docker-compose down

# Stop containers and wipe database (fresh start)
docker-compose down -v

💻 Running Locally for Development
Prerequisites

Java 21+ (JDK, not JRE)
Node.js 20+
MySQL 8 running on port 3307

Backend
bashcd backend
./mvnw spring-boot:run
Runs on http://localhost:8080
Frontend
bashcd frontend/scriptdojo-frontend
npm install
npm run dev
Runs on http://localhost:5173 with API proxied to http://localhost:8080


🧪 Testing
Backend — JUnit + MockMvc (152 tests)
bashcd backend
./mvnw test
Test coverage:

AuthControllerTest — 13 tests
CompilerControllerTest — 14 tests
FileControllerTest — 20 tests
PermissionControllerTest — 10 tests
RoomControllerTest — 10 tests
SecurityConfigTest — 12 tests
SecurityTest — 12 tests
ActiveUsersServiceTest — 13 tests
CompilationServiceTest — 14 tests
ExecutionServiceTest — 9 tests
PermissionServiceTest — 16 tests
UserServiceTest — 9 tests

Frontend — Playwright E2E (51 tests)
bashcd frontend/scriptdojo-frontend

# Requires backend running on localhost:8080 or Vite dev server on localhost:5173
npx playwright test

# View test report
npx playwright show-report
Total: 203 automated tests — 100% pass rate

🔐 Environment Variables
VariableDescriptionDefaultSPRING_PROFILES_ACTIVESpring profile (docker or default)—APP_BASE_URLPublic URL used in share linkshttp://localhost:8080SPRING_DATASOURCE_URLMySQL connection URLSet in application-docker.propertiesSPRING_DATASOURCE_USERNAMEMySQL usernamescriptdojoSPRING_DATASOURCE_PASSWORDMySQL passwordSet in docker-compose

🎓 Use Cases

University coding labs — Lecturer shares one link with 30 students; watches every keystroke in real time
Technical interviews — Interviewer and candidate edit the same file; run code together and see identical output
Leaving Certificate Computer Science — Teacher creates a file, students join without accounts or Git knowledge
Pair programming — Two developers collaborate on the same file from different locations


📖 How It Works
Real-Time Synchronisation
Every keystroke is debounced at 80ms and sent via STOMP to /app/room/{fileId}/editor. The server persists the change to MySQL immediately and broadcasts the canonical version to all subscribers. All clients (including the sender) replace their editor content with this database version — guaranteeing eventual consistency with no Operational Transformation complexity.
Guest Access
The host clicks Generate Share Link → Spring Boot creates a Room entity with an 11-character alphanumeric ID and returns the full URL. Guests open the URL, React fetches /api/room/join/{roomId}, and the Monaco editor loads with the current file content. No login, no account, no installation.
Permission Control
Host clicks Grant Edit → POST /api/permissions/grant-edit → server verifies ownership → persists a Permission entity → broadcasts a permission message to /topic/room/{fileId}/permissions → guest's Monaco instance calls editor.updateOptions({ readOnly: false }).

👤 Author
Conor Griffin
Student ID: X22327033
BSc (Hons) Computing — National College of Ireland
Final Year Project 2025/2026

📄 License
This project is developed as a final-year academic project at the National College of Ireland.