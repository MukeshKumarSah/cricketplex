# Multi-Team Support - Backend Implementation

## Overview
Implemented backend support for supporters to own multiple teams with the following restrictions:
1. Cannot have teams in the same country
2. Cannot bid between own teams on transfer market
3. Teams are completely independent (finances, players, ratings)

## Changes Made

### 1. Entity Changes

#### Team.java
- Changed `@OneToOne` to `@ManyToOne` relationship with User
- Removed `unique = true` constraint from `owner_id`
- Added `teamOrder` field (1 = primary, 2 = secondary)

#### User.java
- Added `activeTeamId` field to track currently active team

### 2. Repository Updates

#### TeamRepository.java
- Added `findByOwnerOrderByTeamOrderAsc(User owner)` - Get all teams for a user
- Added `countByOwner(User owner)` - Count teams per user

### 3. New Controller: MultiTeamController.java

#### Endpoints:
- `GET /api/multi-team/my-teams` - Get all user's teams with active status
- `POST /api/multi-team/switch/{teamId}` - Switch active team
- `POST /api/multi-team/create-secondary` - Create secondary team (supporter only)

#### Validation:
- Supporter status check
- Maximum 2 teams per user
- Country restriction (cannot have teams in same country)
- Team ownership verification

### 4. Transfer Market Updates

#### TransferMarketController.java
- Updated `placeBid()` to prevent bidding between own teams
- Added validation: Check if bidder and seller have the same owner
- Updated `getTeam()` helper to use active team

### 5. Authentication Updates

#### AuthResponse.java (DTO)
- Added `activeTeamId` field
- Added `hasMultipleTeams` boolean flag

#### AuthService.java
- Updated `getCurrentUser()` to return multi-team info
- Updated `buildAuthResponse()` to include active team data

### 6. Utility Class

#### TeamHelper.java
- Created centralized helper for getting active team
- Supports both UserPrincipal and User entity
- Falls back to first team for backwards compatibility

### 7. Database Migration

#### migration_multi_team.sql
- Add `active_team_id` column to users table
- Add `team_order` column to teams table
- Remove unique constraint from `owner_id`
- Set default values for existing data
- Add performance indexes

## API Documentation

### Get My Teams
```http
GET /api/multi-team/my-teams
Authorization: Bearer {token}

Response:
{
  "teams": [
    {
      "id": "uuid",
      "teamName": "string",
      "country": "string",
      "teamOrder": 1,
      "teamProfilePicUrl": "string",
      "odiRating": 1000,
      "t20Rating": 1000,
      "fcRating": 1000,
      "funds": 50000,
      "isActive": true
    }
  ],
  "activeTeamId": "uuid",
  "canCreateSecondary": true
}
```

### Switch Team
```http
POST /api/multi-team/switch/{teamId}
Authorization: Bearer {token}

Response:
{
  "message": "Switched to Team Name",
  "activeTeamId": "uuid",
  "teamName": "string"
}
```

### Create Secondary Team
```http
POST /api/multi-team/create-secondary
Authorization: Bearer {token}
Content-Type: application/json

{
  "teamName": "string",
  "country": "string",
  "groundName": "string" (optional)
}

Response:
{
  "message": "Secondary team created successfully",
  "team": {
    "id": "uuid",
    "teamName": "string",
    "country": "string",
    "teamOrder": 2
  }
}
```

## Business Rules

### Team Creation
- User must be a supporter (`isSupporter = true`)
- Maximum 2 teams per user
- Team name must be unique
- Country must be different from existing team(s)

### Team Switching
- Only own teams can be selected
- Active team context used for all operations
- Falls back to first team if active team not set

### Transfer Market
- Cannot bid on listings from own other teams
- Validation checks owner IDs of both teams
- Error message: "You cannot bid on players from your other team"

## Migration Steps

1. **Backup Database** - Always backup before schema changes
2. **Run Migration Script** - Execute `migration_multi_team.sql`
3. **Verify Data** - Check that existing teams have `team_order = 1`
4. **Deploy Backend** - Deploy updated application code
5. **Test Endpoints** - Verify multi-team functionality

## Testing Checklist

- [ ] Existing users can still access their team
- [ ] Supporter can create second team
- [ ] Non-supporter cannot create second team
- [ ] Cannot create team in same country
- [ ] Can switch between teams
- [ ] Active team context works in all operations
- [ ] Cannot bid between own teams
- [ ] Team data remains independent
- [ ] All existing functionality works

## Next Steps (Frontend)

1. Create team switcher UI component
2. Update AuthContext to track active team
3. Add "Create Secondary Team" flow
4. Add team switcher to header/sidebar
5. Test team switching across all pages

## Notes

- Backwards compatible with single-team users
- Active team automatically set to first team if null
- All financial operations use active team context
- Teams are truly independent - no shared resources
