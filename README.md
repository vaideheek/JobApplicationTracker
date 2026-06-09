# JobTrack Web

A modern full-stack job application tracker built with React + Vite + TypeScript (frontend) and Java 17 Spring Boot (backend), backed by PostgreSQL.

## Features

- **Dashboard** — Overview with total applications, interviews, offers, rejections, and weekly stats
- **Applications List** — Searchable, filterable, paginated table with inline actions
- **Add/Edit Applications** — Clean form with all fields including source, salary range, priority, follow-up/deadline dates
- **Application Detail** — Full details view with notes and status change timeline
- **Search & Filter** — Search by company name or job title, filter by status
- **Status Tracking** — Automatic timeline of status changes (Applied → Interview → Offer, etc.)
- **Priority Suggestions** — Automatic priority suggestions (`LOW`, `MEDIUM`, `HIGH`) based on status, upcoming dates (interviews or deadlines within 7 days), referrals, and target companies, with an interactive preview and click-to-accept logic on manual entry and email imports.


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
| GET    | `/api/dashboard/insights`| Dashboard insights & analytics |
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

## Priority Suggestion Rules

JobTrack automatically evaluates application fields to suggest a priority level:
- **LOW**: Suggested if status is `REJECTED` or `WITHDRAWN` (this status always overrides all other rules).
- **HIGH**: Suggested if any of the following apply (in order of highest precedence):
  - Status is `OFFER`, `INTERVIEW`, or `ASSESSMENT`.
  - Follow-up or Interview date is within 7 days (today through today + 7 days).
  - Application deadline date is within 7 days (today through today + 7 days).
  - Source contains "Referral" (case-insensitive).
  - Company name matches our target list (e.g. Google, Microsoft, Amazon, Meta, Apple, Arm, JetBrains, Deloitte, SAP, Spotify, revolut, Bloomberg, JPMorgan, Goldman Sachs, etc.).
- **MEDIUM**: Suggested if:
  - Salary range is specified (as a baseline minimum).
  - Fallback default (if no other rules apply).

## Dashboard Insights & Recommended Actions

JobTrack includes an analytics and action layer on the main Dashboard:
- **Insights Cards**:
  - *High Priority*: Total applications marked with HIGH priority.
  - *Follow-ups Needed*: Active applications (not REJECTED, WITHDRAWN, or OFFER) where follow-up date is in the past.
  - *Upcoming Interviews*: Applications with status `INTERVIEW` where the follow-up date is between today and today + 14 days (inclusive).
  - *Stale Applications*: Active applications that have not been updated in 14+ days.
- **Conversion Rates & Analytics**:
  - *Response Rate*: `(ASSESSMENT + INTERVIEW + OFFER + REJECTED) / total * 100` (excludes `IN_REVIEW` and `APPLIED`).
  - *Interview Conversion Rate*: Unique applications that reached `INTERVIEW` status divided by total applications.
  - *Offer Conversion Rate*: Unique applications that reached `OFFER` status divided by total applications.
  - *Top Companies*: Ranked list of top 5 companies by application counts.
- **Recommended Actions Widget**:
  Generates up to 6 of the most urgent recommendations, sorted by:
  1. *Overdue follow-ups first* (oldest follow-up date first).
  2. *Upcoming interviews/assessments next* (soonest date first).
  3. *Stale applications last* (longest stale application first).

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

---

## Testing Email Import (Sample Templates)

You can copy and paste the following templates into the **Email Import** page to test different status extractions:

### 1. Application Confirmation
```text
Subject: Application Received: Software Engineer at Google
From: careers@google.com
To: candidate@gmail.com
Date: 2026-06-09

Hi Candidate,
Thank you for applying for the Software Engineer position at Google. We have successfully received your application. Our recruiting team will review your qualifications and contact you if there is a match.
```

### 2. Interview Invite
```text
Subject: Interview Schedule for Frontend Developer at Meta
From: recruiter@meta.com
To: candidate@gmail.com
Date: 2026-06-12

Hi Candidate,
We are impressed by your background and would like to schedule a phone screen interview for the Frontend Developer role at Meta. Please let us know your availability for a Zoom call on June 20, 2026.
```

### 3. Assessment Invite
```text
Subject: Coding Assessment for Data Scientist position at Apple
From: recruiter@apple.com
To: candidate@gmail.com
Date: 2026-06-14

Hi Candidate,
Thanks for your interest in the Data Scientist role at Apple. The next step in our process is an online assessment. Please complete the HackerRank challenge by June 18, 2026.
```

### 4. Rejection
```text
Subject: Your application to Stripe
From: careers@stripe.com
To: candidate@gmail.com

Hi Candidate,
Thank you for applying for the Solutions Architect position at Stripe. Unfortunately, after careful review of your application, we have decided not to move forward with your candidacy at this time as we are pursuing other candidates. We wish you the best of luck in your job search.
```

### 5. Offer
```text
Subject: Job Offer: Product Manager at Netflix
From: recruiter@netflix.com
To: candidate@gmail.com

Hi Candidate,
We are pleased to offer you the position of Product Manager at Netflix. We were incredibly impressed by your interviews and are excited to have you join the team! Please find your formal offer letter attached.
```