# CricketPlex Product Requirements Document (Current App)

Version: Current implementation snapshot  
Scope: End-to-end product behavior and user flow based on existing code

## 1. Product Overview
CricketPlex is a web-based cricket management game where users build and manage a club, compete in league and friendly matches (T20/ODI/FC), and progress through squad development, transfers, finances, and infrastructure upgrades.

## 2. Product Goals
1. Deliver a deep cricket manager gameplay loop.
2. Simulate realistic match outcomes with meaningful pre-match strategy.
3. Provide long-term progression via leagues, academy, and market systems.
4. Maintain secure, server-authoritative match and results handling.

## 3. Personas
1. Guest: Visits auth pages and signs up/logs in.
2. Manager (authenticated): Uses all gameplay features.
3. Admin: Operates maintenance, pools, league generation, and simulation tools.

## 4. Tech Stack
1. Frontend: React + Vite
2. Backend: Spring Boot (Java 17)
3. Database: PostgreSQL
4. Object storage: MinIO
5. Auth: JWT Bearer

## 5. In-Scope Modules
1. Authentication and onboarding
2. Team creation and profile/settings
3. League standings, fixtures, and stats
4. Match center (preview, lineup, live, commentary, scorecard)
5. Friendly challenges
6. Academy pulls and training
7. Transfer market (with real-time updates)
8. Ground and pitch management
9. Finances
10. Stats and search
11. Chat and activity feed
12. Admin control modules

## 6. Primary User Flow

### 6.1 Auth and Team Setup
1. User signs up or logs in.
2. If `teamSetupDone=false`, user is routed to `/team-setup`.
3. User picks country/team name, checks availability, and creates team.
4. User lands on home dashboard.

### 6.2 Daily Gameplay Loop
1. Review dashboard cards, upcoming/live matches, and league context.
2. Manage squad and pick lineup for scheduled fixtures.
3. For FC matches, configure declaration/follow-on strategy.
4. Enter match flow:
1. Preview
2. Live
3. Commentary
4. Scorecard (post-completion)
5. Improve team systems:
1. Academy pulls and training assignments
2. Transfer listing/bidding
3. Ground/pitch optimization
4. Finance tracking
6. Use social features:
1. Friendly challenges
2. Chat
3. Search/scouting

### 6.3 Long-Term Progression
1. Fixtures advance by round and season.
2. Completed matches feed standings and player/team stats.
3. Periodic systems apply wages/training/fitness/aging updates.
4. League progression and historical records continue across seasons.

## 7. Match Lifecycle and States

### 7.1 Standard Lifecycle
1. Fixture created as `SCHEDULED`.
2. Managers submit lineup (`/api/match/{fixtureId}/lineup`).
3. Match simulation starts (`/api/match/simulate/{fixtureId}`).
4. During live window, fixture is `IN_PROGRESS` (or FC split state).
5. Finalized status becomes `COMPLETED`.

### 7.2 FC Lifecycle
1. Day 1 simulation can end as `FC_DAY1_COMPLETE`.
2. Managers set/update FC strategy between days.
3. Day 2 resumes and ends as `COMPLETED` when done.

### 7.3 Result Visibility Rules
1. Match/result data is backend authoritative.
2. Sensitive outputs (winner/full scorecard/commentary detail) should only be public after completion.
3. Current hardening in `MatchSimController` returns locked payloads for non-completed fixtures on sensitive endpoints.

## 8. Functional Requirements
1. JWT protection for private endpoints.
2. Ownership validation on team-specific actions.
3. League table calculations from completed fixtures only.
4. Match simulation support for T20/ODI/FC.
5. FC strategy persistence and day-to-day continuation.
6. Transfer flow: list, bid, cancel, retire/fire, finalize sales.
7. Academy flow: overview, upgrades, pulls, training assignment/history.
8. Friendly challenge lifecycle: send, accept, decline, cancel, simulate.
9. Ground management: seating, pitch defaults, attendance history.
10. Settings and profile updates including media upload.
11. Search and stats views for player/team/league insights.
12. Admin utilities for league/player pool/system operations.

## 9. Non-Functional Requirements
1. Security: never trust frontend; enforce server-side checks.
2. Reliability: transactional updates for critical flows (match, transfer, finance).
3. Performance: efficient queries and selective payloads.
4. Observability: activity/history endpoints for traceability.
5. Scalability readiness: modular service/controller boundaries.

## 10. Frontend Route Flow (High-Level)
1. Public:
1. `/signup`
2. `/login`
2. Onboarding:
1. `/team-setup`
3. Core app shell:
1. `/`
2. `/settings`
3. `/squad`
4. `/academy`
5. `/transfer-market`
6. `/finances`
7. `/stats`
8. `/search`
9. `/matches`
10. `/league/:id`
11. `/team-list`
12. `/team/:teamId`
13. `/player/:id`
14. `/challenges`
14. Match routes:
1. `/match/:fixtureId/preview`
2. `/match/:fixtureId/lineup`
3. `/match/:fixtureId/fc-strategy`
4. `/match/:fixtureId/live`
5. `/match/:fixtureId/commentary`
6. `/match/:fixtureId/scorecard`
5. Admin routes:
1. `/admin/players`
2. `/admin/leagues`
3. `/admin/bots`
4. `/admin/sim`

## 11. Backend API Domains (Current)
1. `/api/auth`
2. `/api/team`
3. `/api/leagues`
4. `/api/match`
5. `/api/challenges`
6. `/api/academy`
7. `/api/transfer`
8. `/api/ground`
9. `/api/finances`
10. `/api/stats`
11. `/api/search`
12. `/api/chat`
13. `/api/activity`
14. `/api/settings`
15. `/api/admin/*`

## 12. Dependencies and Integrations
1. PostgreSQL for core game data.
2. MinIO for profile/team media.
3. WebSocket topic usage for transfer market live updates.

## 13. Key Risks and Gaps
1. Some frontend screens may assume full result shape during live statuses and need consistent locked-state handling.
2. Match-adjacent endpoints should be reviewed to prevent pre-completion leakage via derived summaries.
3. DevTools visibility cannot be eliminated; only payload sensitivity can be controlled.

## 14. Success Metrics (Recommended)
1. New user conversion: signup to team setup completion.
2. D1/D7 retention.
3. Matches played per active manager per week.
4. Academy and transfer participation rates.
5. Friendly challenge acceptance/completion rate.
6. Error rate on lineup/simulate/result flows.
7. Security KPI: zero pre-completion result leaks.

## 15. Future PRD Extensions
1. Detailed endpoint contracts with sample request/response.
2. UX wireframes per match state (`SCHEDULED`, `IN_PROGRESS`, `FC_DAY1_COMPLETE`, `COMPLETED`).
3. Acceptance test matrix for no-leak result policy.
4. Economy balancing model for transfer, wages, and training ROI.

