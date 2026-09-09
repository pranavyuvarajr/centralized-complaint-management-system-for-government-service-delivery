# Complaint Management System - Backend

Spring Boot REST API for the Centralized Complaint Management System.

## Tech Stack
- Java 21 LTS
- Spring Boot 3.5.x
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL (Neon)

## Getting Started

### Prerequisites
- Java 21+
- Maven 3.9+
- PostgreSQL (local or Neon)

### Local Setup

1. **Clone and navigate:**
   ```bash
   cd complaint-management-backend
   ```

2. **Configure database** in `src/main/resources/application.properties`:
   ```
   spring.datasource.url=jdbc:postgresql://localhost:5432/complaint_db
   spring.datasource.username=postgres
   spring.datasource.password=postgres
   ```

3. **Run:**
   ```bash
   mvn spring-boot:run
   ```

4. The API will be available at `http://localhost:8080`

### Default Admin Credentials
- Email: `admin@gov.in`
- Password: `admin123`

### Default Departments (auto-created)
- Roads and Highways
- Water Supply
- Sanitation
- Electricity
- Transportation
- Public Infrastructure

## Deploy to Railway

1. Push code to GitHub.
2. Connect repo to Railway.
3. Set environment variables:
   - `DATABASE_URL` — Neon PostgreSQL JDBC URL
   - `DATABASE_USERNAME` — Neon username
   - `DATABASE_PASSWORD` — Neon password
   - `JWT_SECRET` — a strong secret string (min 256 bits)
   - `CORS_ORIGINS` — your Vercel frontend URL
4. Railway will auto-detect Spring Boot and deploy.

## API Endpoints

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login |

### Complaints
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/complaints` | Create complaint |
| GET | `/api/complaints` | Get complaints (role-filtered) |
| GET | `/api/complaints/{id}` | Get complaint details |
| PUT | `/api/complaints/{id}/status` | Update status |
| GET | `/api/complaints/{id}/history` | Get status history |

### Departments
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/departments` | List all departments |
| POST | `/api/departments` | Create department |
| PUT | `/api/departments/{id}` | Update department |
| DELETE | `/api/departments/{id}` | Delete department |

### Admin
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/users` | List all users |
| GET | `/api/admin/users/role/{role}` | Users by role |
| POST | `/api/admin/users` | Create user |
| DELETE | `/api/admin/users/{id}` | Delete user |
| PUT | `/api/admin/complaints/{id}/assign/{deptId}` | Assign complaint |
| GET | `/api/admin/stats` | Dashboard stats |
