# Complaint Management System — Backend

Spring Boot REST API for the Centralized Complaint Management System.

## Technology Stack

- Java 21
- Spring Boot 3.5.0
- Spring Web
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- JWT (JJWT 0.12.6)
- Spring Validation
- Spring Boot Actuator
- Lombok
- Maven

## Responsibilities

The backend provides:

- Authentication and registration.
- JWT-based authorization.
- Role-based access control for citizens, officials and administrators.
- Complaint creation, tracking and lifecycle management.
- Department and official assignment.
- Complaint priority management.
- Complaint status history.
- Comments/messages.
- File attachments.
- Citizen feedback and ratings.
- Notifications.
- Administrative dashboard statistics.
- User and department management.
- Complaint category management.
- Administrative audit logs.
- Login rate limiting.
- Health monitoring.

## Project Structure

```text
src/main/java/com/project/complaint/
├── controller/       # REST API endpoints
├── service/          # Business logic
├── repository/       # Spring Data repositories
├── entity/           # JPA entities
├── dto/              # Request/response DTOs
├── security/         # JWT authentication and rate limiting
├── specification/    # Dynamic complaint filtering
└── config/           # Security, CORS, initialization and error handling
```

## Requirements

- Java 21+
- Maven 3.9+
- PostgreSQL

## Local Configuration

The application uses environment variables with local defaults.

Example:

```env
DATABASE_URL=jdbc:postgresql://localhost:5432/complaint_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres

JWT_SECRET=ChangeThisToALongRandomSecret
JWT_EXPIRATION_MS=86400000

CORS_ORIGINS=http://localhost:5173

PORT=8080
UPLOAD_DIR=uploads
```

See `.env.example` for the complete configuration list.

## Run Locally

### Development

```bash
mvn spring-boot:run
```

### Build

```bash
mvn clean package -DskipTests
```

The generated executable JAR is:

```text
target/complaint-management-1.0.0.jar
```

### Run the JAR

```bash
java -jar target/complaint-management-1.0.0.jar
```

The API runs on:

```text
http://localhost:8080
```

## Database

The application uses PostgreSQL and Hibernate/JPA.

The schema is automatically created/updated using:

```properties
spring.jpa.hibernate.ddl-auto=update
```

For the deployed application, PostgreSQL is hosted by Neon.

## Deployment

The completed application is deployed using:

```text
Oracle Cloud Infrastructure (OCI)
        │
        ▼
Ubuntu VM
        │
        ├── systemd
        │      └── Spring Boot
        │
        └── Nginx
               │
               ▼
        Nginx + Let's Encrypt IP Certificate
```

The backend runs on port `8080` and is managed by `systemd`.

This means the backend:

- Continues running after SSH is closed.
- Starts automatically when the VM boots.
- Is not dependent on an open SSH terminal.

The Nginx tunnel is also managed by `systemd` and forwards the public HTTPS endpoint to the Spring Boot service.

## Production/Cloud Environment Variables

Set these values in the deployment environment rather than committing secrets:

```env
DATABASE_URL=jdbc:postgresql://<host>:5432/<database>
DATABASE_USERNAME=<database-user>
DATABASE_PASSWORD=<database-password>

JWT_SECRET=<long-random-secret>
JWT_EXPIRATION_MS=86400000

CORS_ORIGINS=https://<frontend-domain>

PORT=8080
UPLOAD_DIR=uploads
```

## API Endpoints

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register a user |
| POST | `/api/auth/login` | Authenticate and obtain a JWT |

### Complaints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/complaints` | Create a complaint |
| GET | `/api/complaints` | List complaints according to the authenticated role |
| GET | `/api/complaints/{id}` | Get complaint details |
| GET | `/api/complaints/track/{complaintNumber}` | Public complaint tracking |
| PUT | `/api/complaints/{id}/status` | Update complaint status |
| PATCH | `/api/complaints/{id}/priority` | Update complaint priority |
| POST | `/api/complaints/{id}/reopen` | Reopen a complaint |
| POST | `/api/complaints/{id}/close` | Close a complaint |
| GET | `/api/complaints/{id}/history` | Get status history |
| GET | `/api/complaints/dashboard/citizen` | Citizen dashboard data |
| GET | `/api/complaints/dashboard/official` | Official dashboard data |

### Departments

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/departments` | List departments |
| POST | `/api/departments` | Create department |
| PUT | `/api/departments/{id}` | Update department |
| PATCH | `/api/departments/{id}/toggle` | Activate/deactivate department |
| DELETE | `/api/departments/{id}` | Delete department |

### Categories

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/categories` | List active categories |
| GET | `/api/categories/all` | List all categories |
| POST | `/api/categories` | Create category |
| PUT | `/api/categories/{id}` | Update category |
| PATCH | `/api/categories/{id}/toggle` | Activate/deactivate category |
| DELETE | `/api/categories/{id}` | Delete category |

### Comments

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/complaints/{complaintId}/comments` | Get complaint comments |
| POST | `/api/complaints/{complaintId}/comments` | Add a comment |

### Feedback

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/complaints/{complaintId}/feedback` | Get complaint feedback |
| POST | `/api/complaints/{complaintId}/feedback` | Submit complaint feedback |

### Attachments

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/complaints/{complaintId}/attachments` | Upload an attachment |
| GET | `/api/complaints/{complaintId}/attachments` | List complaint attachments |
| GET | `/api/attachments/{attachmentId}/download` | Download an attachment |

### Notifications

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/notifications` | List notifications |
| GET | `/api/notifications/unread-count` | Get unread count |
| PATCH | `/api/notifications/{id}/read` | Mark notification as read |
| PATCH | `/api/notifications/read-all` | Mark all notifications as read |

### User

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/users/me` | Get current user |
| PATCH | `/api/users/me` | Update current user |
| POST | `/api/users/me/password` | Change current user's password |

### Administration

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/admin/users` | List users |
| GET | `/api/admin/users/role/{role}` | Filter users by role |
| POST | `/api/admin/users` | Create user |
| PATCH | `/api/admin/users/{id}/active` | Activate/deactivate user |
| PATCH | `/api/admin/users/{id}/password` | Change a user's password |
| DELETE | `/api/admin/users/{id}` | Delete user |
| PATCH | `/api/admin/complaints/{id}/assign` | Assign complaint |
| PATCH | `/api/admin/complaints/{id}/priority` | Change complaint priority |
| GET | `/api/admin/stats` | Dashboard statistics |
| GET | `/api/admin/audit-logs` | Administrative audit log |

### Health

```text
GET /actuator/health
```

## Demo Accounts

The data initializer creates demonstration accounts:

```text
Admin:
  Email: admin@example.com
  Password: admin123

Official:
  Email: official.water@example.com
  Password: official123

Official:
  Email: official.publicworks@example.com
  Password: official123

Citizen:
  Email: citizen@example.com
  Password: citizen123

Citizen:
  Email: citizen2@example.com
  Password: citizen123
```

These credentials are for demonstration only.

## Security Notes

- Passwords are hashed using BCrypt.
- JWT authentication is stateless.
- Role checks are enforced on the backend.
- CORS is configured through `CORS_ORIGINS`.
- Login rate limiting is implemented in memory.
- Production deployments should use strong secrets supplied through environment variables.
