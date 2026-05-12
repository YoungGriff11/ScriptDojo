# STAGE 1 — Build the React frontend
FROM node:20-slim AS frontend-build

WORKDIR /app/frontend

# Copy package files first (better Docker layer caching)
COPY frontend/scriptdojo-frontend/package*.json ./

# Install dependencies
RUN npm ci

# Copy the rest of the frontend source
COPY frontend/scriptdojo-frontend/ ./

# Build the React app into static files
RUN npm run build

# STAGE 2 — Build the Spring Boot backend (with React inside it)

FROM eclipse-temurin:21-jdk-jammy AS backend-build

WORKDIR /app/backend

# Copy Maven wrapper and pom.xml first (better layer caching)
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./

# Make mvnw executable (Windows may strip this)
RUN chmod +x mvnw

# Download dependencies (cached as a layer if pom.xml unchanged)
RUN ./mvnw dependency:go-offline -B

# Copy the backend source code
COPY backend/src/ ./src/

# Copy the React build output into Spring Boot's static resources folder
# Spring Boot serves these automatically — no Node.js container needed
COPY --from=frontend-build /app/frontend/dist/ ./src/main/resources/static/

# Build the JAR, skipping tests
RUN ./mvnw package -DskipTests -B

# STAGE 3 — Final runtime image

FROM eclipse-temurin:21-jdk-jammy AS runtime

WORKDIR /app

# Copy only the built JAR from Stage 2 — nothing else
COPY --from=backend-build /app/backend/target/*.jar app.jar

# Spring Boot listens on port 8080
EXPOSE 8080

# Activate the docker profile so application-docker.properties loads
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]