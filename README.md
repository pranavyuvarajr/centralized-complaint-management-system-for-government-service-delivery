# Centralized Complaint Management System for Government Service Delivery

A full-stack web application enabling citizens to register and track government service
complaints, officials to manage and resolve them, and administrators to monitor the entire
system. Built as an academic demonstration project — not affiliated with any real government
body.

## Architecture

```
React (Vite) ──HTTPS──▶ Spring Boot REST API ──JDBC──▶ PostgreSQL
```

The frontend and backend are fully decoupled and talk only over the REST API, so each can be
deployed and scaled independently (static host + web service + managed Postgres), or run
together locally with a single `docker compose up`.

## Features

- **Citizens**: submit complaints (with category, location, and file attachment), track status
  by a public reference number (`CMP-2026-000001`) with no login required, message the assigned
  official, reopen or close resolved complaints, and leave a star rating + feedback.
- **Officials**: department-scoped queue with search/filter/pagination, enforced status-transition
  rules (no skipping steps), priority handling, resolution notes, and an overdue-complaint stat.
- **Admins**: system dashboard with live charts (by category, by department, monthly volume),
  complaint assignment (department + specific official, validated server-side), category
  management, department management (soft-deactivate, never hard-delete), user management
  (activate/deactivate), and a searchable audit log of administrative actions.
- **Cross-cutting**: JWT auth with independent backend-side role checks (not just hidden UI),
  in-app notifications, full status-history timeline per complaint, login rate limiting,
  consistent JSON success/error responses, and no data or statistics that aren't computed from
  the actual database.

## Project Structure

```
complaint-management-system/
├── complaint-management-frontend/   # React + Vite
│   ├── src/
│   │   ├── components/              # Navbar, Sidebar, badges, notifications, pagination…
│   │   ├── pages/                   # All page components
│   │   ├── services/api.js          # Axios API layer
│   │   ├── context/AuthContext.jsx  # Auth state management
│   │   ├── routes/                  # ProtectedRoute
│   │   ├── App.jsx                  # Router config
│   │   └── main.jsx                 # Entry point
│   ├── Dockerfile / nginx.conf
│   └── package.json
│
├── complaint-management-backend/    # Java Spring Boot
│   ├── src/main/java/com/project/complaint/
│   │   ├── controller/              # REST controllers
│   │   ├── service/                 # Business logic
│   │   ├── repository/              # JPA repositories
│   │   ├── entity/                  # Database entities
│   │   ├── dto/                     # Data transfer objects
│   │   ├── security/                # JWT auth + login rate limiter
│   │   ├── specification/           # Dynamic complaint search/filter
│   │   └── config/                  # Security, CORS, seed data, error handling
│   ├── Dockerfile
│   └── pom.xml
│
├── docker-compose.yml                # One-command local stack (Postgres + backend + frontend)
└── README.md
```

## Demo Accounts

Seeded automatically on first run (see `DataInitializer.java`):

| Role      | Email                              | Password      |
|-----------|-------------------------------------|---------------|
| Admin     | `admin@example.com`                | `admin123`    |
| Official  | `official.water@example.com`       | `official123` |
| Official  | `official.publicworks@example.com` | `official123` |
| Citizen   | `citizen@example.com`              | `citizen123`  |
| Citizen   | `citizen2@example.com`             | `citizen123`  |

These are demonstration credentials only — change or remove them before any real deployment.

## Running Locally — Option A: Docker (easiest)

Requires only Docker installed. This starts Postgres, the backend, and the frontend together.

```bash
git clone <your-repo-url>
cd complaint-management-system
docker compose up --build
```

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- The database schema and seed data are created automatically on first boot.

## Running Locally — Option B: Manual (no Docker)

**Prerequisites:** Java 17+, Maven, Node 18+, PostgreSQL running locally.

```bash
# 1. Create the database
createdb complaint_db

# 2. Backend
cd complaint-management-backend
cp .env.example .env        # edit if your Postgres credentials differ
mvn spring-boot:run
# API now running on http://localhost:8080

# 3. Frontend (separate terminal)
cd complaint-management-frontend
cp .env.example .env.local
npm install
npm run dev
# App now running on http://localhost:5173
```

The schema is created/updated automatically by Hibernate (`ddl-auto=update`) — no manual
migrations needed, and nothing about the existing schema is changed by this setup beyond adding
new columns/tables for the newer features (priority, categories, comments, attachments,
feedback, notifications, audit log).

## Deploying to the Cloud

The app needs no code changes to deploy — every environment-specific value (database
connection, JWT secret, CORS origin, port) is read from environment variables with sane local
defaults, so cloud deployment is purely a matter of configuration.

**Suggested free/low-cost combination:**

| Component  | Where it can run                          |
|------------|--------------------------------------------|
| Database   | Neon / Railway Postgres / Supabase |
| Backend    | Railway / Render (deploy the `complaint-management-backend` folder, or its Dockerfile) |
| Frontend   | Vercel / Netlify / Render Static Site (deploy the `complaint-management-frontend` folder) |

**Backend environment variables to set on your host:**

```
DATABASE_URL=jdbc:postgresql://<host>:5432/<db>
DATABASE_USERNAME=<user>
DATABASE_PASSWORD=<password>
JWT_SECRET=<a long random string>
CORS_ORIGINS=https://<your-frontend-domain>
PORT=8080                # most platforms set this for you automatically
```

**Frontend environment variable:**

```
VITE_API_URL=https://<your-backend-domain>
```

Set it at build time (most static hosts let you set env vars per-project) — Vite bakes it into
the build.

If your platform builds from a Dockerfile directly, both `complaint-management-backend/Dockerfile`
and `complaint-management-frontend/Dockerfile` are ready to use as-is; otherwise most platforms
auto-detect a Maven project and a Node/Vite project respectively without any Dockerfile at all.

A `GET /actuator/health` endpoint is exposed on the backend for platforms that require a health
check URL.

## Notes on File Uploads in the Cloud

Attachments are stored on local disk (`UPLOAD_DIR`, default `uploads/`) to keep the project
simple, as specified. Most container-based hosts (Railway, Render) reset local disk on
redeploy, so uploaded files won't survive a redeploy unless you attach a persistent volume.
That's fine for a demonstration/college project; swapping in cloud object storage (S3-compatible)
would be the natural next step for a production system, but is intentionally out of scope here
to keep the stack simple.

## Known Limitations

- This was assembled without a Java compiler available in the authoring environment, so while
  every file was written carefully against the existing code's own conventions, a first
  `mvn clean package` locally is the real compile check — please run it before deploying.
- The frontend build (`npm run build`) was verified to succeed here.
- Rate limiting on login is in-memory only (resets on backend restart) — sufficient for a
  demo, not for a multi-instance production deployment.
