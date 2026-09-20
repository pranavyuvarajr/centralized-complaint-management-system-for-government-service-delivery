# Complaint Management System - Frontend

React-based frontend for the Centralized Complaint Management System.

## Tech Stack
- React 19
- Vite
- React Router 7
- Axios

## Getting Started

### Prerequisites
- Node.js 22 LTS
- npm (comes with Node.js)

### Local Setup

1. **Clone and navigate:**
   ```bash
   cd complaint-management-frontend
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Configure API URL** — create `.env` from example:
   ```bash
   cp .env.example .env
   ```
   Edit `VITE_API_URL` to point to your backend.

4. **Start dev server:**
   ```bash
   npm run dev
   ```

5. Open `http://localhost:5173`

## Deploy to Vercel

1. Push code to GitHub.
2. Import repo in Vercel.
3. Set environment variable:
   - `VITE_API_URL` = your Railway backend URL (e.g. `https://your-app.up.railway.app`)
4. Deploy — Vercel auto-detects Vite.

## Pages

| Path | Role | Description |
|------|------|-------------|
| `/` | Public | Landing page |
| `/login` | Public | Login |
| `/register` | Public | Citizen registration |
| `/citizen` | Citizen | My complaints |
| `/citizen/new` | Citizen | Submit complaint |
| `/citizen/complaint/:id` | Citizen | Complaint details |
| `/official` | Official | Department complaints |
| `/official/complaint/:id` | Official | View & update complaint |
| `/admin` | Admin | Dashboard with stats |
| `/admin/complaints` | Admin | All complaints + assign |
| `/admin/users` | Admin | User management |
| `/admin/departments` | Admin | Department CRUD |
