# Multi-Team Feature - Complete Implementation Guide

## 🎯 Overview
Complete multi-team support implementation allowing supporters to manage up to 2 independent teams with strict business rules for game balance.

## ✅ Implementation Status
**COMPLETE** - Backend & Frontend Fully Implemented (2024)

---

## 📋 Business Rules

| Rule | Description |
|------|-------------|
| **Supporter Only** | Only users with supporter status can create secondary teams |
| **Max Teams** | Maximum 2 teams per user |
| **Country Restriction** | Teams must be from different countries |
| **Transfer Block** | Cannot bid on players from own teams |
| **Independence** | Each team has separate squad, finances, matches, stats |
| **Active Tracking** | System tracks currently active team per session |

---

## 🗄️ Backend Implementation

### 1. Database Schema Changes

**File**: `backend/src/main/resources/db/migration/V69__multi_team_support.sql`

**Changes**:
- Added `active_team_id` column to `users` table
- Added `team_order` column to `teams` table (1=primary, 2=secondary)
- Removed unique constraint on `owner_id` in `teams` table
- Added indexes for performance
- Added foreign key constraint for referential integrity

**How to Run**:
```bash
# Flyway will automatically run V69__multi_team_support.sql on application startup
# Or manually if needed:
psql -U postgres -d cricketplex -f backend/src/main/resources/db/migration/V69__multi_team_support.sql
```

---

### 2. Core Backend Files

#### 📄 Team.java
**Location**: `backend/src/main/java/com/cricketplex/entity/Team.java`

**Changes**:
- `@OneToOne` → `@ManyToOne` relationship with User
- Added `teamOrder` field (Integer, default 1)

#### 📄 User.java  
**Location**: `backend/src/main/java/com/cricketplex/entity/User.java`

**Changes**:
- Added `activeTeamId` field (UUID) for session tracking

#### 📄 TeamRepository.java
**Location**: `backend/src/main/java/com/cricketplex/repository/TeamRepository.java`

**New Methods**:
```java
List<Team> findByOwnerOrderByTeamOrderAsc(User owner);
long countByOwner(User owner);
```

---

### 3. New REST Controller

#### 📄 MultiTeamController.java
**Location**: `backend/src/main/java/com/cricketplex/controller/MultiTeamController.java`

**Endpoints**:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/multi-team/my-teams` | Get all user's teams with active status |
| POST | `/api/multi-team/switch/{teamId}` | Switch to different team |
| POST | `/api/multi-team/create-secondary` | Create secondary team (with validation) |

**Key Validations**:
- ✅ Supporter status verification
- ✅ Max team count check (≤ 2)
- ✅ Country uniqueness validation
- ✅ Ownership verification

---

### 4. Utility Helper

#### 📄 TeamHelper.java
**Location**: `backend/src/main/java/com/cricketplex/util/TeamHelper.java`

**Purpose**: Centralized logic for retrieving active team

**Logic Flow**:
1. Check if user has `activeTeamId` set
2. If yes → return that team
3. If no → return first team ordered by `teamOrder`

---

### 5. Transfer Market Protection

#### 📄 TransferMarketController.java
**Location**: `backend/src/main/java/com/cricketplex/controller/TransferMarketController.java`

**Updated Method**: `placeBid()`

**Added Validation**:
```java
// Prevent bidding between own teams
if (sellerTeam.getOwner().getId().equals(bidderTeam.getOwner().getId())) {
    return error("You cannot bid on players from your other team");
}
```

---

### 6. Authentication Enhancement

#### 📄 AuthResponse.java
**Location**: `backend/src/main/java/com/cricketplex/dto/AuthResponse.java`

**New Fields in UserInfo**:
```java
private String activeTeamId;
private Boolean hasMultipleTeams;
```

#### 📄 AuthService.java
**Location**: `backend/src/main/java/com/cricketplex/service/AuthService.java`

**Updated Methods**:
- `getCurrentUser()` - Includes multi-team data
- `buildAuthResponse()` - Populates activeTeamId and hasMultipleTeams

---

## 🎨 Frontend Implementation

### 1. API Integration

#### 📄 auth.js
**Location**: `frontend/src/api/auth.js`

**New Functions**:
```javascript
export const getMyTeams = () => api.get('/api/multi-team/my-teams');
export const switchTeam = (teamId) => api.post(`/api/multi-team/switch/${teamId}`);
export const createSecondaryTeam = (data) => api.post('/api/multi-team/create-secondary', data);
```

---

### 2. Context Enhancement

#### 📄 AuthContext.jsx
**Location**: `frontend/src/context/AuthContext.jsx`

**New Function**: `refreshUser()`
- Re-fetches user data from `/auth/me`
- Updates both state and localStorage
- Used after team operations to sync data

**Export**: Added to context provider value

---

### 3. TeamSwitcher Component

#### 📁 Component Files
- `frontend/src/components/TeamSwitcher/TeamSwitcher.jsx`
- `frontend/src/components/TeamSwitcher/TeamSwitcher.css`

**Features**:
- ✨ Dropdown showing all user's teams
- ✅ Active team highlighted with checkmark
- 🎨 Team logos or placeholder initials
- 🌍 Country display for each team
- ➕ "Create Secondary Team" button (if eligible)
- 🖱️ Click outside to close
- 🔄 Full page reload after switch

**Visibility**: Only shows for supporters with multiple teams or eligible to create

---

### 4. Sidebar Integration

#### 📄 Sidebar.jsx
**Location**: `frontend/src/components/Sidebar/Sidebar.jsx`

**Integration**:
```jsx
{isOpen && (
  <div className="sidebar-team-switcher">
    <TeamSwitcher />
  </div>
)}
```

**Position**: Below sidebar brand, above navigation links

---

### 5. Secondary Team Setup Page

#### 📁 Page Files
- `frontend/src/pages/TeamSetupSecondary/TeamSetupSecondary.jsx`
- `frontend/src/pages/TeamSetupSecondary/TeamSetupSecondary.css`

**Route**: `/team-setup-secondary`

**Features**:
- 🛡️ **Supporter Check**: Redirects non-supporters to membership page
- 📊 **Max Team Check**: Redirects if user already has 2 teams
- 📋 **Existing Teams Display**: Shows current teams with logos
- ℹ️ **Info Box**: Displays multi-team rules
- 📝 **Form Validation**: Client-side validation with error messages

**Form Fields**:
| Field | Validation |
|-------|-----------|
| Team Name | 3-50 characters, required |
| Country | Must be different from existing teams, required |
| Ground Name | 3-50 characters, required |

**Success Flow**: Create → Refresh User → Redirect to Team Setup

---

### 6. Routing

#### 📄 App.jsx
**Location**: `frontend/src/App.jsx`

**New Route**:
```jsx
<Route
  path="/team-setup-secondary"
  element={
    <TeamSetupGuard>
      <TeamSetupSecondary />
    </TeamSetupGuard>
  }
/>
```

---

## 🔄 User Flows

### Flow 1: Create Secondary Team
```
1. User (supporter) opens TeamSwitcher in sidebar
2. Clicks "Create Secondary Team"
3. Redirected to /team-setup-secondary
4. System validates:
   ✓ User is supporter (else → /membership)
   ✓ User has < 2 teams (else → /)
5. User fills form (team name, country, ground)
6. Submit → API call → Success
7. Refresh auth context
8. Redirect to /team-setup for squad setup
```

### Flow 2: Switch Between Teams
```
1. User opens TeamSwitcher dropdown
2. Sees all teams with active indicator
3. Clicks inactive team
4. API call to switch
5. Update activeTeamId in database
6. Refresh user context
7. Page reloads → shows data for new active team
```

### Flow 3: Transfer Market Protection
```
1. User lists player from Team A on transfer market
2. User switches to Team B
3. User attempts to bid on their own player
4. System blocks with error:
   "You cannot bid on players from your other team"
```

---

## 🧪 Testing Checklist

### Backend Tests
- [x] Supporter can create secondary team
- [x] Non-supporter cannot create secondary team
- [x] Cannot create more than 2 teams
- [x] Cannot create teams in same country
- [x] Can switch between owned teams
- [x] Cannot switch to team not owned
- [x] Transfer market blocks inter-team bidding
- [x] Active team correctly retrieved via TeamHelper
- [x] Auth response includes multi-team data

### Frontend Tests
- [x] TeamSwitcher appears for supporters
- [x] TeamSwitcher shows all user's teams
- [x] Active team correctly highlighted
- [x] Can switch teams via dropdown
- [x] Page reloads after switch
- [x] Create button appears if eligible
- [x] Create page validates supporter status
- [x] Country dropdown filters correctly
- [x] Form validation works
- [x] Success flow redirects correctly

---

## 📚 API Documentation

### GET `/api/multi-team/my-teams`

**Response**:
```json
{
  "teams": [
    {
      "id": "uuid",
      "teamName": "Mumbai Masters",
      "country": "India",
      "teamProfilePicUrl": "logo.png",
      "isActive": true
    },
    {
      "id": "uuid",
      "teamName": "Sydney Thunder",
      "country": "Australia",
      "teamProfilePicUrl": null,
      "isActive": false
    }
  ],
  "canCreateSecondary": false
}
```

---

### POST `/api/multi-team/switch/{teamId}`

**Success Response**:
```json
{
  "message": "Successfully switched to Mumbai Masters"
}
```

**Error Responses**:
- `404`: Team not found or not owned by user
- `400`: Invalid team ID

---

### POST `/api/multi-team/create-secondary`

**Request Body**:
```json
{
  "teamName": "Sydney Thunder",
  "country": "Australia",
  "groundName": "Thunder Stadium"
}
```

**Success Response**:
```json
{
  "message": "Secondary team created successfully",
  "teamId": "uuid"
}
```

**Error Responses**:
- `403`: User is not a supporter
- `400`: Already has 2 teams
- `400`: Country already used by another team
- `400`: Invalid input (validation errors)

---

## 🔒 Security Considerations

| Area | Protection |
|------|------------|
| **Ownership** | All operations validate team ownership |
| **Supporter Status** | Backend validates supporter flag |
| **Transfer Market** | Server-side check prevents inter-team trading |
| **Active Team** | Cannot manipulate other user's active team |
| **Country Validation** | Backend enforces country uniqueness |
| **Session Integrity** | Active team tracked server-side |

---

## 🚀 Future Enhancements

1. **Team Statistics Dashboard** - Compare performance between teams
2. **Team Management Page** - Centralized view/management
3. **Team Deletion** - Allow deletion of secondary team
4. **Team Transfer** - Transfer ownership to another user
5. **Quick Switch in Header** - Additional switcher location
6. **Team Notifications** - Separate channels per team
7. **Team Notes** - Internal comments per team
8. **Performance Comparison** - Side-by-side metrics

---

## 🛠️ Maintenance & Support

### Key Files to Monitor
| File | Purpose |
|------|---------|
| `TeamHelper.java` | Core team retrieval logic |
| `MultiTeamController.java` | All multi-team operations |
| `TransferMarketController.java` | Bid validation |
| `TeamSwitcher.jsx` | UI component for switching |
| `AuthContext.jsx` | User state management |

### Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| Active team not updating | Verify `refreshUser()` is called after switch |
| Can't create secondary | Check supporter status and team count |
| Wrong team data showing | Ensure page reloads after team switch |
| Transfer bid not blocked | Verify ownership comparison in controller |
| TeamSwitcher not visible | Check user.isSupporter and hasMultipleTeams |

---

## 📦 Deployment Checklist

### Pre-Deployment
- [x] Run database migration script
- [x] Verify all tests pass
- [x] Check for compilation errors
- [x] Review security validations
- [x] Update documentation

### Post-Deployment
- [ ] Verify migration ran successfully
- [ ] Test create secondary team flow
- [ ] Test team switching
- [ ] Test transfer market protection
- [ ] Monitor error logs
- [ ] Check performance metrics

---

## 🔄 Rollback Procedure

If issues arise post-deployment:

### Database Rollback
```sql
-- Remove foreign key
ALTER TABLE users DROP CONSTRAINT fk_users_active_team;

-- Remove columns
ALTER TABLE users DROP COLUMN active_team_id;
ALTER TABLE teams DROP COLUMN team_order;

-- Restore unique constraint
ALTER TABLE teams ADD CONSTRAINT uk_owner_id UNIQUE (owner_id);

-- Remove secondary teams (optional)
DELETE FROM teams WHERE team_order = 2;
```

### Code Rollback
- Revert backend commits for multi-team feature
- Revert frontend commits for TeamSwitcher and related components
- Restore @OneToOne relationship in Team entity
- Remove MultiTeamController

---

## 📊 Database Schema Reference

### users table
```sql
Column: active_team_id
Type: UUID
Nullable: true
Foreign Key: teams(id) ON DELETE SET NULL
Index: idx_users_active_team_id
```

### teams table
```sql
Column: team_order
Type: INTEGER
Default: 1
Nullable: false
Values: 1 (primary), 2 (secondary)

Column: owner_id
Constraint: REMOVED unique constraint (now allows multiple teams per owner)
Index: idx_teams_owner_id
```

---

## 📖 Component Props Reference

### TeamSwitcher Component
- **Props**: None (uses AuthContext)
- **Dependencies**: 
  - `useAuth()` hook
  - `getMyTeams`, `switchTeam` API functions
  - `refreshUser` context method
- **State**: 
  - `teams` - All user's teams
  - `showDropdown` - Dropdown visibility
  - `switching` - Loading state
  - `canCreateSecondary` - Eligibility flag

---

**Document Version**: 1.0.0  
**Last Updated**: 2024  
**Status**: ✅ Production Ready
