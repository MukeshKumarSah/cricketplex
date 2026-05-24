# User-Generated Commentary System

## Overview

The commentary submission system allows users to contribute their own match commentary that will appear during live match simulations. All submissions go through an admin approval workflow before being used in matches.

## Database Schema

**Table:** `commentary_submissions`

### Core Fields
- `id` (UUID) - Primary key
- `user_id` (UUID) - Author
- `team_id` (UUID) - Author's team
- `commentary_text` (VARCHAR 500) - The commentary with placeholders
- `status` (VARCHAR 20) - `pending`, `approved`, or `rejected`

### Match Filters (Required)
- `match_format` - `T20`, `ODI`, or `TEST`
- `phase` - `powerplay`, `middle`, `death`, `pressure`, or `cruising`
- `bowler_type` - `FAST_SEAM`, `FAST`, `MEDIUM_FAST`, `MEDIUM`, or `SPINNER`
- `event_type` - Event code (see Event Types below)

### Optional Context Filters
- `wicket_situation` - `early_wickets`, `collapse`, `rebuilding`, `set_partnership`
- `batsman_state` - `new_batsman`, `settling`, `set`, `milestone_approaching`
- `match_pressure` - `low`, `medium`, `high`

### Metadata
- `extra_tags` (JSON array) - Tags like `catch_taken`, `great_fielding`, etc.
- `placeholders_used` (JSON array) - Extracted placeholders
- `admin_notes` (TEXT) - Rejection reason or notes
- `reviewed_by`, `reviewed_at` - Admin who reviewed
- `times_used` (INT) - Usage counter (incremented when used in matches)
- `last_edited_by`, `last_edited_at` - Edit tracking
- `created_at`, `updated_at` - Timestamps

## Event Types

### Run Scoring Events
- `0` - Dot ball
- `1` - Single
- `2` - Two runs
- `3` - Three runs
- `4` - Four (boundary)
- `6` - Six

### Extras
- `1LB`, `2LB`, `3LB`, `4LB` - Leg byes
- `1WD`, `2WD`, `3WD`, `4WD`, `5WD`, `6WD`, `7WD` - Wides
- `1NB`, `2NB`, `3NB`, `4NB`, `5NB`, `6NB`, `7NB` - No balls

### Wicket Types
- `BOWLED` - Bowled
- `CAUGHT` - Caught (excluding caught behind)
- `LBW` - Leg before wicket
- `RUN_OUT` - Run out
- `STUMPED` - Stumped
- `HIT_WICKET` - Hit wicket
- `CAUGHT_AND_BOWLED` - Caught and bowled

## Placeholders

Use placeholders in square brackets to insert dynamic values:

### Player Placeholders
- `[batsman]` - Current batsman's name
- `[non_striker]` - Non-striker's name
- `[bowler]` - Current bowler's name
- `[fielder]` - Fielder's name (for catches, run-outs)
- `[keeper]` - Wicket-keeper's name (for stumpings, keeper catches)

### Team Placeholders
- `[batting_team]` - Batting team name
- `[bowling_team]` - Bowling team name

### Stats Placeholders
- `[runs]` - Runs scored on this delivery
- `[score]` - Current total score
- `[wickets]` - Current wickets down
- `[overs]` - Current overs bowled

### Example
```
[batsman] launches it over long-on for [runs]! What a shot to bring up [batting_team]'s century!
```

## Extra Tags

Optional tags for more specific commentary matching:

- `catch_taken` - Great catch taken
- `catch_dropped` - Catch dropped
- `great_fielding` - Excellent fielding effort
- `misfield` - Fielding error
- `strike_farming` - Batter protecting partner
- `milestone` - Personal milestone (50, 100, etc.)
- `partnership` - Partnership milestone
- `pressure_building` - Building pressure on batting team
- `momentum_shift` - Match momentum shifting
- `tail_ender_involved` - Tail-ender batting or bowling
- `last_over_drama` - Final over excitement

## API Endpoints

### User Endpoints (`/api/commentary`)

#### Submit Commentary
```
POST /api/commentary/submit
Body: {
  "teamId": "uuid",
  "commentaryText": "string",
  "matchFormat": "T20|ODI|TEST",
  "phase": "string",
  "bowlerType": "string",
  "eventType": "string",
  "wicketSituation": "string (optional)",
  "batsmanState": "string (optional)",
  "matchPressure": "string (optional)",
  "extraTags": ["string"] (optional)
}

Response: {
  "success": true|false,
  "errors": ["string"],
  "warnings": ["string"],
  "submissionId": "uuid",
  "similarCommentary": [...]
}
```

#### Get My Submissions
```
GET /api/commentary/my-submissions

Response: {
  "total": int,
  "pending": int,
  "approved": int,
  "rejected": int,
  "submissions": [...]
}
```

#### Edit Own Pending Commentary
```
PUT /api/commentary/{id}
Body: {
  "commentaryText": "string"
}
```

#### Delete Own Pending Commentary
```
DELETE /api/commentary/{id}
```

#### Excel Import - Validate
```
POST /api/commentary/import/validate
FormData: file (xlsx)

Response: {
  "success": true|false,
  "total": int,
  "valid": int,
  "warnings": int,
  "errors": int,
  "validRows": [...],
  "warningRows": [...],
  "errorRows": [...]
}
```

#### Excel Import - Confirm
```
POST /api/commentary/import/confirm
Body: {
  "teamId": "uuid",
  "rows": [validated rows from previous step]
}
```

#### Download Excel Template
```
GET /api/commentary/import/template

Returns: commentary_template.xlsx
```

#### Get Filter Options
```
GET /api/commentary/filter-options

Returns: All valid options for filters (match formats, phases, event types, etc.)
```

### Admin Endpoints (`/api/admin/commentary`)

All require `ROLE_ADMIN` authority.

#### Get Pending Submissions
```
GET /api/admin/commentary/pending
```

#### Get Approved Submissions
```
GET /api/admin/commentary/approved
```

#### Get Rejected Submissions
```
GET /api/admin/commentary/rejected
```

#### Approve Commentary
```
POST /api/admin/commentary/{id}/approve
```

#### Reject Commentary
```
POST /api/admin/commentary/{id}/reject
Body: {
  "reason": "string"
}
```

#### Edit Any Commentary
```
PUT /api/admin/commentary/{id}
Body: {
  "commentaryText": "string"
}
```

#### Delete Any Commentary
```
DELETE /api/admin/commentary/{id}
```

#### Bulk Approve
```
POST /api/admin/commentary/bulk-approve
Body: {
  "ids": ["uuid"]
}
```

#### Bulk Reject
```
POST /api/admin/commentary/bulk-reject
Body: {
  "ids": ["uuid"],
  "reason": "string"
}
```

## Match Engine Integration

The `MatchEngine` service automatically queries user-submitted commentary during match simulation for **all event types**:

### Integrated Event Types

**✅ Fully Integrated:**
- `0` - Dot balls
- `1` - Singles
- `2` - Twos
- `3` - Threes
- `4` - Boundaries (fours)
- `6` - Sixes
- `BOWLED` - Bowled wickets
- `CAUGHT` - Caught wickets
- `LBW` - LBW wickets
- `RUN_OUT` - Run out wickets
- `STUMPED` - Stumped wickets
- `HIT_WICKET` - Hit wicket
- `CAUGHT_AND_BOWLED` - Caught and bowled
- `CAUGHT_BEHIND` - Caught behind (keeper catches)
- `DROPPED` - Dropped catches
- `1WD`, `2WD`, `3WD`, `4WD`, `5WD`, `6WD`, `7WD` - Wides with runs
- `1NB`, `2NB`, `3NB`, `4NB`, `5NB`, `6NB`, `7NB` - No balls with runs
- `1LB`, `2LB`, `3LB`, `4LB` - Leg byes
- `1BYE`, `2BYE`, `3BYE`, `4BYE` - Byes

### How It Works

1. **Context Detection**: For each delivery, determines:
   - Match format (T20/ODI/TEST)
   - Phase (powerplay/middle/death)
   - Bowler type
   - Event type (runs, wicket type, extras)

2. **Commentary Query**: Searches for approved user commentary matching the context (optional filters like wicket situation, pressure are set to null for broader matches)

3. **Random Selection**: If multiple matching commentaries exist, picks one randomly

4. **Placeholder Replacement**: Replaces all placeholders with actual match data:
   - Player names (batsman, bowler, fielder, keeper)
   - Team names
   - Match statistics (runs, score, wickets, overs)

5. **Fallback**: If no user commentary found, uses hardcoded commentary

6. **Usage Tracking**: Increments `times_used` counter (silently fails if error)

### Phase Determination

**T20 Phases:**
- Powerplay: Overs 1-6
- Middle: Overs 7-15
- Death: Overs 16-20

**ODI Phases:**
- Powerplay: Overs 1-10
- Middle: Overs 11-40
- Death: Overs 41-50

**TEST/FC:**
- Middle: All overs (no phase distinction currently)

## Validation Rules

### Submission Validation
- Commentary text: 10-500 characters
- All placeholders must be valid (from allowed list)
- Context-specific placeholders require appropriate tags:
  - `[fielder]` requires `catch_taken`, `catch_dropped`, `great_fielding`, `misfield`, or `RUN_OUT` event
  - `[keeper]` requires `STUMPED` event or `catch_taken` tag
- Exact duplicates (same text + event type) rejected
- 80% similarity detection within same event type (warning only)

### Edit Permissions
- **Users**: Can edit only their own `pending` submissions
- **Admins**: Can edit any submission at any status

### Delete Permissions
- **Users**: Can delete only their own `pending` submissions
- **Admins**: Can delete any submission at any status

## Excel Import Format

### Template Columns
1. **Commentary Text** (required) - The commentary with placeholders
2. **Match Format** (required) - T20, ODI, or TEST
3. **Phase** (required) - powerplay, middle, death, pressure, or cruising
4. **Bowler Type** (required) - FAST_SEAM, FAST, MEDIUM_FAST, MEDIUM, or SPINNER
5. **Event Type** (required) - See Event Types section
6. **Wicket Situation** (optional)
7. **Batsman State** (optional)
8. **Match Pressure** (optional)
9. **Extra Tags** (optional) - Comma-separated

### Limits
- Maximum 1000 rows per import
- All imported commentaries go to `pending` status
- Validation runs before import, showing errors and warnings

## Database Indexes

### Similarity Search (pg_trgm)
- `idx_commentary_similarity` - Trigram index for similarity detection
- `idx_commentary_event_type` - Speeds up event type filtering

### Query Performance
- `idx_commentary_status` - Filter by status
- `idx_commentary_user_status` - User's submissions by status
- Composite indexes on filter columns for match engine queries

## Notes

- All database operations are transactional
- Commentary service silently falls back on errors (doesn't break match simulation)
- Similarity threshold: 80% (configurable in repository query)
- Phase determination:
  - T20: powerplay (1-6), middle (7-15), death (16-20)
  - ODI: powerplay (1-10), middle (11-40), death (41-50)
  - TEST: middle (no phase distinction currently)

## Future Enhancements

### Phase 2
- Integrate commentary for all event types (singles, dots, wickets, extras)
- Add more granular context filters (match situation, required rate, etc.)
- Gamification: Points/badges for approved commentary, usage leaderboards
- Statistics dashboard for coverage gaps
- Auto-reject duplicates instead of warning

### Phase 3
- Machine learning for quality scoring
- Community voting on commentary
- Seasonal commentary competitions
- Language support (internationalization)
- Commentary variations (formal vs casual tone)
