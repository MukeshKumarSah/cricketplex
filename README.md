# CricketPlex - Cricket Management Game

## Tech Stack
- **Backend:** Java 17 + Spring Boot 3.2.5
- **Frontend:** React 19 + Vite
- **Database:** PostgreSQL 16
- **Object Storage:** MinIO
- **Auth:** JWT (Bearer token)

## Getting Started

### 1. Start Infrastructure (PostgreSQL + MinIO)
```bash
docker-compose up -d
```
This starts:
- PostgreSQL on port **5432** (user: `cricketplex`, password: `cricketplex_secret`, db: `cricketplex`)
- MinIO on port **9000** (console at **9001**, user: `minioadmin`, password: `minioadmin`)

### 2. Run Backend
```bash
cd backend
mvn spring-boot:run
```
The API starts on **http://localhost:8080**

### 3. Run Frontend
```bash
cd frontend
npm install
npm run dev
```
The app opens on **http://localhost:5173**

## API Endpoints

### Auth
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/auth/signup` | Register new user | No |
| POST | `/api/auth/login` | Login | No |
| GET | `/api/auth/me` | Get current user | Yes |

### Team
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/team/setup` | Create team | Yes |

## Database Schema

### users
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| name | VARCHAR | |
| username | VARCHAR | Unique |
| email | VARCHAR | Unique |
| password | VARCHAR | BCrypt hashed |
| role | ENUM | USER, ADMIN |
| is_supporter | BOOLEAN | Extra feature flag |
| is_sub_admin | BOOLEAN | Extra feature flag |
| accepted_terms | BOOLEAN | |
| profile_pic_url | VARCHAR | MinIO URL |
| team_setup_done | BOOLEAN | |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

### teams
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| team_name | VARCHAR | |
| country | VARCHAR | |
| owner_id | UUID | FK → users, unique |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |
