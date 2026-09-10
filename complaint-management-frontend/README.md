# Complaint Management System — Frontend

React/Vite frontend for the Centralized Complaint Management System.

## Technology Stack

- React 19
- Vite 6
- React Router 7
- Axios
- CSS
- Node.js 22 LTS recommended

## Features

### Public

- Landing page.
- Citizen registration.
- Login.
- Public complaint tracking using a complaint reference number.

### Citizen

- Citizen dashboard.
- Submit complaints.
- Add complaint details and attachments.
- View complaints.
- View complaint details.
- View status history.
- Communicate through comments.
- Reopen/close eligible complaints.
- Submit ratings and feedback.
- View notifications.

### Official

- Department complaint dashboard.
- Search/filter/pagination.
- Complaint details.
- Complaint processing.
- Status updates.
- Priority handling.
- Resolution information.
- Notifications.

### Administrator

- System dashboard.
- Complaint statistics.
- Complaint assignment.
- Complaint priority management.
- User management.
- Department management.
- Complaint category management.
- Administrative audit log.

## Project Structure

```text
src/
├── components/       # Shared UI components
├── pages/             # Application pages
├── services/          # API/Axios layer
├── context/           # Authentication/application state
├── routes/            # Route protection
├── App.jsx            # Application/router configuration
├── main.jsx           # React entry point
└── index.css          # Global styles
```

## Requirements

- Node.js 22 LTS recommended
- npm

## Local Setup

From the project directory:

```bash
npm install
```

Configure the backend API URL:

```env
VITE_API_URL=http://localhost:8080
```

The example configuration is available in:

```text
.env.example
```

Start the development server:

```bash
npm run dev
```

The frontend is normally available at:

```text
http://localhost:5173
```

## Production Build

Create a production build:

```bash
npm run build
```

Preview the production build locally:

```bash
npm run preview
```

## API Configuration

The frontend communicates with the Spring Boot REST API through the `VITE_API_URL` environment variable.

Local example:

```env
VITE_API_URL=http://localhost:8080
```

Deployed example:

```env
VITE_API_URL=https://<current-backend-https-endpoint>
```

> Vite embeds `VITE_*` environment variables during the build. If the API URL changes, the frontend must be rebuilt and redeployed.

## Deployment

The completed frontend is deployed on Vercel.

**Live application:**

```text
https://centralized-complaint-management-sy.vercel.app/
```

The deployed architecture is:

```text
Browser
   │
   ▼
Vercel
React + Vite
   │
   │ HTTPS
   ▼
Cloudflare Quick Tunnel
   │
   ▼
OCI Ubuntu VM
Spring Boot :8080
   │
   ▼
Neon PostgreSQL
```

The Vercel frontend communicates with the backend over HTTPS through the Cloudflare Quick Tunnel.

This is important because a browser blocks an HTTPS frontend from making an insecure HTTP request to the backend as mixed active content.

## Vercel Deployment Steps

1. Push the project to GitHub.
2. Import the frontend project into Vercel.
3. Configure the project to use the `complaint-management-frontend` directory if deploying from the repository root.
4. Set:

```env
VITE_API_URL=https://<current-backend-https-endpoint>
```

5. Build using:

```bash
npm run build
```

6. Deploy.

## SPA Routing

The application uses React Router for client-side routing.

The included Nginx configuration uses:

```nginx
try_files $uri $uri/ /index.html;
```

so that client-side routes fall back to the React application when served through Nginx/Docker.

Vercel deployments should also be configured to serve the SPA entry point for client-side routes when required by the hosting configuration.

## Application Routes

| Path | Access | Purpose |
|---|---|---|
| `/` | Public | Landing page |
| `/login` | Public | Login |
| `/register` | Public | Citizen registration |
| `/citizen` | Citizen | Citizen dashboard |
| `/citizen/new` | Citizen | Submit complaint |
| `/citizen/complaint/:id` | Citizen | Complaint details |
| `/official` | Official | Official dashboard |
| `/official/complaint/:id` | Official | Official complaint view |
| `/admin` | Admin | Administration dashboard |
| `/admin/complaints` | Admin | Complaint management |
| `/admin/users` | Admin | User management |
| `/admin/departments` | Admin | Department management |

## Docker

A frontend Dockerfile is included for containerized deployment.

For local development, the recommended approach is:

```bash
npm install
npm run dev
```

For a production build:

```bash
npm run build
```

## Environment Variables

Only the API base URL is required by the frontend:

```env
VITE_API_URL=http://localhost:8080
```

For a public deployment, use the HTTPS backend endpoint.

Do not place database credentials, JWT secrets or other backend secrets in frontend environment variables. Vite variables are bundled into the client application and are therefore public.

## Deployment Limitation

The current deployment uses a Cloudflare **Quick Tunnel**.

Quick Tunnel provides a temporary HTTPS URL suitable for development/demo deployments, but the URL is not intended to be a permanent production hostname.

For a long-term production deployment, use a permanent HTTPS backend endpoint such as:

- A named Cloudflare Tunnel with a custom domain.
- An OCI-native HTTPS configuration.
- Another production API hosting platform.

## Live Application

```text
https://centralized-complaint-management-sy.vercel.app/
```
