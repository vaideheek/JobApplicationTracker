# JobTrack Web

A modern full-stack job application tracker built with React + Vite + TypeScript (frontend) and Java 17 Spring Boot (backend), backed by PostgreSQL.

## Features

- **Dashboard** — Overview with total applications, interviews, offers, rejections, and weekly stats
- **Applications List** — Searchable, filterable, paginated table with inline actions
- **Add/Edit Applications** — Clean form with all fields including source, salary range, priority, follow-up/deadline dates
- **Application Detail** — Full details view with notes and status change timeline
- **Search & Filter** — Search by company name or job title, filter by status
- **Status Tracking** — Automatic timeline of status changes (Applied → Interview → Offer, etc.)

## Tech Stack

| Layer    | Technology                        |
|----------|-----------------------------------|
| Frontend | React 18, Vite 5, TypeScript      |
| Styling  | Tailwind CSS 3                    |
| Backend  | Java 17, Spring Boot 3.5          |
| Database | PostgreSQL 15                     |
| API      | REST (JSON)                       |
| Icons    | Lucide React                      |

## Prerequisites

- **Java 17+** — [Download](https://adoptium.net/)
- **Node.js 18+** — [Download](https://nodejs.org/) or install via nvm: `nvm install 20`
- **Docker** — [Download Docker Desktop](https://www.docker.com/products/docker-desktop/)

## Quick Start (Mac)

### 1. Start PostgreSQL via Docker

```bash
cd /path/to/JobApplicationTracker
docker compose up -d
```

This starts PostgreSQL on port 5432 with:
- Database: `jobtrack_db`
- User: `postgres`
- Password: `postgres`

### 2. Start the Backend

```bash
cd backend
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

Wait until you see: `Started JobTrackApplication in X seconds`

### 3. Start the Frontend

Open a **new terminal**:

```bash
cd frontend
npm install    # first time only
npm run dev
```

The app will be available at `http://localhost:5173`.

## Project Structure

```
JobApplicationTracker/
├── docker-compose.yml          # PostgreSQL container
├── backend/                    # Spring Boot API
│   ├── pom.xml
│   └── src/main/java/com/jobtrack/
│       ├── config/             # CORS configuration
│       ├── controller/         # REST endpoints
│       ├── dto/                # Request/Response DTOs
│       ├── entity/             # JPA entities
│       ├── enums/              # Status & Priority enums
│       ├── exception/          # Error handling
│       ├── repository/         # Data access
│       └── service/            # Business logic
├── frontend/                   # React + Vite app
│   ├── src/
│   │   ├── api/                # Axios API client
│   │   ├── components/         # Reusable UI components
│   │   ├── pages/              # Page components
│   │   └── types/              # TypeScript types
│   └── package.json
└── README.md
```

## API Endpoints

| Method | Endpoint                | Description              |
|--------|-------------------------|--------------------------|
| GET    | `/api/dashboard/stats`  | Dashboard statistics     |
| GET    | `/api/applications`     | List applications (paginated, filterable) |
| GET    | `/api/applications/:id` | Get application detail   |
| POST   | `/api/applications`     | Create application       |
| PUT    | `/api/applications/:id` | Update application       |
| DELETE | `/api/applications/:id` | Delete application       |

### Query Parameters for GET `/api/applications`

| Param    | Type   | Default         | Description                    |
|----------|--------|-----------------|--------------------------------|
| search   | string | —               | Search company name or job title |
| status   | string | —               | Filter by status enum value    |
| page     | int    | 0               | Page number (0-indexed)        |
| size     | int    | 20              | Page size                      |
| sortBy   | string | lastUpdatedAt   | Sort field                     |
| sortDir  | string | desc            | Sort direction (asc/desc)      |

## Application Fields

| Field            | Type     | Required | Description                |
|------------------|----------|----------|----------------------------|
| companyName      | String   | ✅       | Company name               |
| jobTitle         | String   | ✅       | Job title / role           |
| location         | String   | —        | Job location               |
| jobUrl           | String   | —        | Link to job posting        |
| dateApplied      | Date     | —        | When you applied           |
| status           | Enum     | ✅       | Application status         |
| stage            | String   | —        | Current stage              |
| recruiterEmail   | String   | —        | Recruiter contact          |
| notes            | Text     | —        | Free-form notes            |
| source           | String   | —        | Where you found the job    |
| companyCareerUrl | String   | —        | Company careers page       |
| salaryRange      | String   | —        | Expected salary range      |
| priority         | Enum     | —        | LOW, MEDIUM, HIGH          |
| followUpDate     | Date     | —        | When to follow up          |
| deadlineDate     | Date     | —        | Application deadline       |

## Status Options

`APPLIED` · `IN_REVIEW` · `ASSESSMENT` · `INTERVIEW` · `OFFER` · `REJECTED` · `WITHDRAWN`

## Stopping the App

```bash
# Stop the frontend: Ctrl+C in the terminal running npm run dev
# Stop the backend:  Ctrl+C in the terminal running ./mvnw spring-boot:run
# Stop PostgreSQL:
docker compose down
```

To also delete the database data:
```bash
docker compose down -v
```

## Future Roadmap

- [ ] Gmail integration for automatic application tracking
- [ ] AI-powered email parsing
- [ ] User authentication (JWT)
- [ ] Resume/document attachments
- [ ] Interview scheduling
- [ ] Analytics and reporting