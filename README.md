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
  Photo and location are independent: an admin sets per category whether each is required or optional.
  The location picker has street/address search, a reliable "use my current location", a draggable pin,
  and a map that can be resized or shown full screen. A mobile number is required for citizens and must be unique among citizens. Officials and admins (created by an admin) can leave it empty and add it later in their profile; their numbers are not checked for duplicates.
  Only the citizen can close a resolved complaint; reopening needs a reason and can include a photo.
- **Officials**: department-scoped queue with search/filter/pagination, enforced status-transition
  rules (no skipping steps), priority handling, resolution notes, and an overdue-complaint stat.
  Marking a complaint resolved needs a completion photo whenever its category requires a citizen photo
  (same setting, officials and admins alike); the complaint page then shows reported vs completion photos.
- **Admins**: system dashboard with live charts (by category, by department, monthly volume),
  complaint assignment (department + specific official, validated server-side), category
  management, department management (soft-deactivate, never hard-delete), user management
  (edit details, move officials between departments with a hand-over of their active
  complaints, activate/deactivate), password-confirmed permanent deletion of a complaint,
  and a searchable audit log of administrative actions.
- **Urgent complaints**: a citizen can file a complaint as **Urgent** with a written justification (and a phone
  number on the account). It is routed as usual, the responsible official is alerted by email, calls the citizen,
  and either confirms it as urgent or marks it normal (reason required); the first decision is final and only an
  admin can override it. Priority of such complaints is locked; URGENT can no longer be set by hand. Pending
  decisions trigger 4 reminders (+6h/12h/18h/24h) and then a one-time admin escalation. See *Urgent complaints* below.
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

**Prerequisites:** Java 21+, Maven, Node 18+, PostgreSQL running locally.

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

## Urgent complaints: email alerts and configuration

Officials and admins set one **alert email** on their Profile page and verify it through the emailed link.
Only a verified alert email is ever used (never the login email); with none, alerts arrive as in-app notifications.
Emails contain no phone number and no access token, only a login-protected link to the complaint.

| Variable | Default | Purpose |
|----------|---------|---------|
| `FRONTEND_BASE_URL` | `http://localhost:5173` | Base of the links inside emails |
| `MAIL_ENABLED` | `false` | `false` = log-only (emails and links are logged, nothing is sent); `true` = real SMTP |
| `MAIL_HOST` / `MAIL_PORT` | `smtp-relay.brevo.com` / `587` | SMTP server (STARTTLS) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | empty | SMTP credentials; never commit real values |
| `MAIL_FROM` | `no-reply@localhost` | Sender; must be verified with your provider |
| `URGENCY_REMINDER_INTERVAL` | `PT6H` | Time between the 4 reminders |
| `URGENCY_ESCALATION_DELAY` | `PT5M` | Admin escalation delay after the 4th reminder |
| `URGENCY_SCHEDULER_ENABLED` | `true` | Reminder job on/off (it runs every 60 s) |

**Brevo SMTP:** create a Brevo account, verify a sender address or domain, generate an SMTP key
(SMTP & API settings), then set `MAIL_ENABLED=true`, `MAIL_HOST=smtp-relay.brevo.com`, `MAIL_PORT=587`,
`MAIL_USERNAME=<your Brevo SMTP login>`, `MAIL_PASSWORD=<your SMTP key>`, `MAIL_FROM=<verified sender>`.
Some hosting providers block outbound SMTP ports; if mail does not arrive, check that first and the backend log.
Mail is sent asynchronously after the database commit, so a mail problem never fails a complaint or assignment.
`EmailService` is an interface, so another provider (for example an HTTP mail API) can replace `SmtpEmailService`.

## Known Limitations

- Urgent-complaint reminders run in a single backend instance (duplicates are prevented by conditional database
  updates, but run the job on one instance); on hosts that sleep, reminders are delayed until the app wakes up
  and are then spread out (never sent in a burst). Uploads still live on local disk.

- This was assembled without a Java compiler available in the authoring environment, so while
  every file was written carefully against the existing code's own conventions, a first
  `mvn clean package` locally is the real compile check — please run it before deploying.
- The frontend build (`npm run build`) was verified to succeed here.
- Rate limiting on login is in-memory only (resets on backend restart) — sufficient for a
  demo, not for a multi-instance production deployment.
