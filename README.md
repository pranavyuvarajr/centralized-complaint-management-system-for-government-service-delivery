# Centralized Complaint Management System for Government Service Delivery

A full-stack complaint management platform that provides a centralized workflow for citizens to submit and track complaints, government officials to process complaints within their departments, and administrators to manage the system.

> **Academic project:** This application is a demonstration project and is not affiliated with any real government organization.

## Live Deployment

**Frontend:** https://centralized-complaint-management.vercel.app/

### Deployment Architecture

```text
                         HTTPS
┌─────────────────────┐
│ Vercel              │
│ React + Vite        │
│ Frontend            │
└──────────┬──────────┘
           │
           │ HTTPS REST API
           ▼
┌─────────────────────┐
│ Cloudflare          │
│ Quick Tunnel        │
└──────────┬──────────┘
           │
           │ Tunnel
           ▼
┌─────────────────────┐
│ Oracle Cloud (OCI)  │
│ Ubuntu VM            │
│                      │
│ Spring Boot :8080    │
│ managed by systemd  │
└──────────┬──────────┘
           │
           │ PostgreSQL
           ▼
┌─────────────────────┐
│ Neon PostgreSQL     │
│ Managed Database    │
└─────────────────────┘
```

### Deployment Details

- **Frontend:** React/Vite deployed on Vercel.
- **Backend:** Spring Boot REST API deployed on an Oracle Cloud Infrastructure (OCI) Ubuntu VM.
- **Backend process management:** Spring Boot runs as a `systemd` service, so it continues running after SSH sessions are closed and is configured to start automatically with the VM.
- **HTTPS access to backend:** Cloudflare Quick Tunnel exposes the Spring Boot service through a temporary HTTPS `trycloudflare.com` URL. This allows the HTTPS Vercel frontend to communicate with the HTTP Spring Boot service without mixed-content browser blocking.
- **Cloudflare process management:** The Quick Tunnel runs as a `systemd` service independently of the SSH session.
- **Database:** PostgreSQL hosted on Neon.
- **Current public frontend URL:** `https://centralized-complaint-management-sy.vercel.app/`

> **Quick Tunnel limitation:** The Cloudflare Quick Tunnel is intended for development/testing and does not provide a permanent hostname or uptime guarantee. The generated `trycloudflare.com` URL can change if the tunnel is recreated. For production, a named Cloudflare Tunnel with a domain or another permanent HTTPS solution should be used.

## Features

### Citizen

- Register and log in.
- Submit complaints with:
  - Title
  - Description
  - Category
  - Location
  - File attachments
- View submitted complaints.
- Track a complaint using its public complaint/reference number without logging in.
- View complaint details and status history.
- Communicate with the assigned official through comments/messages.
- Reopen eligible resolved complaints.
- Close eligible complaints.
- Submit star ratings and feedback for complaints.
- Receive in-app notifications.

### Official

- View complaints assigned to their department.
- Search, filter and paginate complaints.
- View complaint details and status history.
- Process complaints through controlled status transitions.
- Update complaint status.
- Handle complaint priority.
- Add resolution information.
- View department-level complaint information and dashboard data.
- Receive notifications.

### Administrator

- View system dashboard and statistics.
- View complaint statistics by category, department and month.
- Manage complaints and assignments.
- Assign complaints to departments and specific officials.
- Change complaint priority.
- Manage departments.
- Activate/deactivate departments.
- Manage users.
- Activate/deactivate users.
- Reset/update user passwords through administrative functions.
- Manage complaint categories.
- View/search administrative audit logs.

### Cross-cutting functionality

- JWT-based authentication.
- Backend-enforced role-based authorization.
- Spring Security.
- Login rate limiting.
- Complaint status history.
- In-app notifications.
- File attachment handling.
- Request validation.
- Centralized JSON error handling.
- PostgreSQL persistence through Spring Data JPA.
- Dynamic complaint search/filtering.
- Health endpoint through Spring Boot Actuator.

## Technology Stack

### Frontend

- React 19
- Vite 6
- React Router 7
- Axios
- CSS

### Backend

- Java 21
- Spring Boot 3.5.0
- Spring Web
- Spring Security
- Spring Data JPA
- Hibernate
- Spring Validation
- Spring Boot Actuator
- JSON Web Tokens (JJWT 0.12.6)
- Lombok
- Maven

### Database

- PostgreSQL
- Neon PostgreSQL for the deployed environment

### Deployment / Infrastructure

- Oracle Cloud Infrastructure (OCI)
- Ubuntu Linux
- Vercel
- Cloudflare Tunnel
- systemd

## Project Structure

```text
complaint-management-system/
│
├── complaint-management-frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/
│   │   ├── context/
│   │   ├── routes/
│   │   ├── App.jsx
│   │   └── main.jsx
│   ├── public/
│   ├── nginx.conf
│   ├── Dockerfile
│   ├── package.json
│   └── .env.example
│
├── complaint-management-backend/
│   ├── src/main/java/com/project/complaint/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   ├── dto/
│   │   ├── security/
│   │   ├── specification/
│   │   └── config/
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── Dockerfile
│   ├── pom.xml
│   └── .env.example
│
├── docker-compose.yml
└── README.md
```

## Database Model

The application uses PostgreSQL with JPA/Hibernate.

The main domain areas include:

```text
User
 ├── Citizen
 └── Official
        │
        └── Department

Complaint
 ├── Category
 ├── Department
 ├── Assigned Official
 ├── Complaint History
 ├── Comments
 ├── Attachments
 ├── Feedback
 └── Notifications
```

Hibernate is configured with:

```properties
spring.jpa.hibernate.ddl-auto=update
```

so the schema is created/updated automatically from the entity model.

## Local Development

### Prerequisites

- Java 21+
- Maven 3.9+
- Node.js 22 LTS recommended
- npm
- PostgreSQL, or Docker

### Backend

From the project root:

```bash
cd complaint-management-backend
mvn clean package -DskipTests
java -jar target/complaint-management-1.0.0.jar
```

Or run directly during development:

```bash
mvn spring-boot:run
```

Backend:

```text
http://localhost:8080
```

### Frontend

In another terminal:

```bash
cd complaint-management-frontend
npm install
npm run dev
```

Frontend:

```text
http://localhost:5173
```

The frontend API URL is configured through:

```env
VITE_API_URL=http://localhost:8080
```

Vite environment variables are evaluated at build time, so the production API URL must be configured before building/deploying the frontend.

## Local Docker Setup

The repository includes a `docker-compose.yml` for running PostgreSQL, the backend and frontend together.

```bash
docker compose up --build
```

The intended local services are:

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
Postgres: localhost:5432
```

The Docker configuration is primarily intended for local development/testing. For the deployed application, the actual environment uses Vercel, OCI, Cloudflare Tunnel and Neon PostgreSQL.

## Environment Variables

### Backend

The backend reads configuration from environment variables:

```env
DATABASE_URL=jdbc:postgresql://<host>:5432/<database>
DATABASE_USERNAME=<username>
DATABASE_PASSWORD=<password>

JWT_SECRET=<long-random-secret>
JWT_EXPIRATION_MS=86400000

CORS_ORIGINS=https://<frontend-domain>

PORT=8080
UPLOAD_DIR=uploads
```

### Frontend

```env
VITE_API_URL=https://<backend-api-url>
```

Do not commit real database passwords, JWT secrets or other credentials to Git.

## Demo Accounts

The application seeds demonstration accounts on first startup.

| Role | Email | Password |
|---|---|---|
| Admin | `admin@example.com` | `admin123` |
| Official | `official.water@example.com` | `official123` |
| Official | `official.publicworks@example.com` | `official123` |
| Citizen | `citizen@example.com` | `citizen123` |
| Citizen | `citizen2@example.com` | `citizen123` |

These credentials are for demonstration purposes only. They must be changed or removed before any real-world deployment.

## API Overview

### Authentication

```text
POST /api/auth/register
POST /api/auth/login
```

### Complaints

```text
POST   /api/complaints
GET    /api/complaints
GET    /api/complaints/{id}
GET    /api/complaints/track/{complaintNumber}
PUT    /api/complaints/{id}/status
PATCH  /api/complaints/{id}/priority
POST   /api/complaints/{id}/reopen
POST   /api/complaints/{id}/close
GET    /api/complaints/{id}/history
GET    /api/complaints/dashboard/citizen
GET    /api/complaints/dashboard/official
```

### Departments

```text
GET    /api/departments
POST   /api/departments
PUT    /api/departments/{id}
PATCH  /api/departments/{id}/toggle
DELETE /api/departments/{id}
```

### Complaint Categories

```text
GET    /api/categories
GET    /api/categories/all
POST   /api/categories
PUT    /api/categories/{id}
PATCH  /api/categories/{id}/toggle
DELETE /api/categories/{id}
```

### Comments

```text
GET  /api/complaints/{complaintId}/comments
POST /api/complaints/{complaintId}/comments
```

### Feedback

```text
GET  /api/complaints/{complaintId}/feedback
POST /api/complaints/{complaintId}/feedback
```

### Attachments

```text
POST /api/complaints/{complaintId}/attachments
GET  /api/complaints/{complaintId}/attachments
GET  /api/attachments/{attachmentId}/download
```

### Notifications

```text
GET   /api/notifications
GET   /api/notifications/unread-count
PATCH /api/notifications/{id}/read
PATCH /api/notifications/read-all
```

### User

```text
GET   /api/users/me
PATCH /api/users/me
POST  /api/users/me/password
```

### Administration

```text
GET   /api/admin/users
GET   /api/admin/users/role/{role}
POST  /api/admin/users
PATCH /api/admin/users/{id}/active
PATCH /api/admin/users/{id}/password
DELETE /api/admin/users/{id}

PATCH /api/admin/complaints/{id}/assign
PATCH /api/admin/complaints/{id}/priority

GET   /api/admin/stats
GET   /api/admin/audit-logs
```

### Health Check

```text
GET /actuator/health
```

## File Uploads

Uploaded complaint attachments are stored on the backend filesystem using the configured:

```env
UPLOAD_DIR=uploads
```

The default maximum file size is:

```text
5 MB per file
```

and the maximum multipart request size is also:

```text
5 MB
```

For a production multi-instance deployment, object storage or a persistent shared storage solution would be preferable.

## Security

The application uses:

- JWT authentication.
- Spring Security authorization.
- Role checks enforced by the backend.
- Password hashing with BCrypt.
- CORS configuration.
- Login rate limiting.
- Validation of incoming requests.
- Administrative audit logging.
- Stateless session management.

Production deployments should use strong, randomly generated JWT secrets and database credentials supplied through environment variables or a secrets manager.

## Current Deployment Operations

The deployed backend is managed by Linux `systemd`.

Spring Boot is configured as a service so that:

```text
OCI VM boots
     ↓
systemd starts Spring Boot
     ↓
Spring Boot listens on :8080
```

Cloudflare Tunnel is also managed as a service:

```text
OCI VM boots
     ↓
systemd starts cloudflared
     ↓
HTTPS tunnel → localhost:8080
```

Therefore, an SSH session is **not required** to keep the application running.

## Limitations

- The current public backend exposure uses a Cloudflare Quick Tunnel, which has no permanent hostname or uptime SLA.
- Quick Tunnel URLs can change when a new tunnel is created.
- Login rate limiting is in-memory and resets when the backend restarts.
- Uploaded files are stored on local disk rather than object storage.
- `ddl-auto=update` is convenient for a project/demo but a production system would normally use explicit database migrations.
- The application is designed as a single-backend deployment; horizontal scaling would require additional considerations for uploads, rate limiting and shared state.

## Future Improvements

Possible production-oriented improvements include:

- Replace Quick Tunnel with a permanent HTTPS endpoint.
- Use a custom domain and a named Cloudflare Tunnel or OCI-native HTTPS architecture.
- Add database migrations with Flyway or Liquibase.
- Move file attachments to object storage.
- Replace in-memory rate limiting with a distributed solution.
- Add automated CI/CD.
- Add automated tests and integration tests.
- Add centralized logging and monitoring.
- Add multiple backend instances behind a load balancer.
- Introduce stronger secret management.

## License

This project is an academic/demo application. Add an appropriate license here if the repository is intended for public redistribution.
