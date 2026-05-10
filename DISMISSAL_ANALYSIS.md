# Wicket Dismissal Distribution Analysis

## Current Distribution (Bug Analysis)

### Base Probabilities
```
Pace bowlers:
- pBowled = 0.22 (22%)
- pCaught = 0.40 (40%)  
- pLBW = 0.15 (15%)
- pStumped = 0.02 (2%)
- pRunOut = 0.08 (8%) ← PROBLEM
- pCaughtBehind = 0.10 (10%)
- pHitWicket = 0.02 (2%)

Spin bowlers:
- pBowled = 0.18 (18%)
- pCaught = 0.40 (40%)
- pLBW = 0.22 (22%)
- pStumped = 0.10 (10%) ← PROBLEM
- pRunOut = 0.08 (8%) ← PROBLEM
- pCaughtBehind = 0.05 (5%)
- pHitWicket = 0.02 (2%)
```

### Additional Modifiers
```
Fielding quality: pCaught += fieldingAvg * 0.002
Keeper skill: pCaughtBehind += keeperSkill * 0.001
Keeper skill: pStumped += keeperSkill * 0.0015 ← Multiplies already high 10% base!

Pitch effects (DUSTY/DRY):
- pStumped += 0.04 (adds 4% more!)
- pLBW += 0.03
```

---

## Real Cricket Statistics

### Typical Dismissal Distribution (Last 5 years ODI/T20)
```
Caught: 55-65%        (Most common - edge behind, in field, etc.)
Bowled: 12-15%        (Solid wood)
LBW: 15-20%           (Pad before wicket)
Caught Behind: 5-8%   (Edge to keeper)
Stumped: 1.5-3%       (RARE - only good spinners + aggressive batters)
Run Out: 2-3%         (RARE - usually only in desperate chases)
Hit Wicket: 0.5-1%    (Very rare)
```

---

## Issues Identified

### Issue 1: Stumping Rate Too High (Spin)
- **Current base:** 10% + keeper skill 0.15% + dusty pitch 4% = **14.15% max**
- **Reality:** 1.5-3%
- **Impact:** You see batters getting stumped out of position constantly, even on normal pitches
- **Fix:** Reduce base stumping to 0.02 (2%) for spin, keep keeper skill bonus modest

### Issue 2: Run Out Rate Too High
- **Current:** 8% for all bowlers
- **Reality:** 2-3% (only happens in desperate situations or foolish running)
- **Impact:** Run outs appear too frequently in normal batting scenarios
- **Fix:** Reduce to 0.03 (3%) and tie to chase pressure/desperation

### Issue 3: Distribution Doesn't Match Reality
- **Current:** Caught only 40% base (should be 55-65%)
- **Current:** Bowled + LBW only 33-40% (should be 30-35%)
- **Impact:** Too many freak dismissals, not enough standard edges/clean bowled

### Issue 4: Cumulative Probability Exceeds 1.0
- Pace: 0.22 + 0.40 + 0.15 + 0.02 + 0.08 + 0.10 + 0.02 = **0.99** (OK, barely)
- Spin: 0.18 + 0.40 + 0.22 + 0.10 + 0.08 + 0.05 + 0.02 = **1.05** (Exceeds 1.0!)
  - When normalized this compresses all probabilities
  - The smallest probabilities get unfairly squeezed
- With keeper skill additions, this compounds further

---

## Recommended Fix

### New Distribution
```
Pace bowlers:
- pBowled = 0.18 (18%)
- pCaught = 0.50 (50%) ← Increase to match reality
- pLBW = 0.15 (15%)
- pStumped = 0.01 (1%)  ← Reduce dramatically
- pRunOut = 0.03 (3%)   ← Reduce significantly
- pCaughtBehind = 0.10 (10%)
- pHitWicket = 0.03 (3%)
Total = 1.00

Spin bowlers:
- pBowled = 0.15 (15%)
- pCaught = 0.50 (50%) ← Increase
- pLBW = 0.20 (20%)
- pStumped = 0.02 (2%)  ← Reduce from 10%!
- pRunOut = 0.03 (3%)   ← Reduce from 8%!
- pCaughtBehind = 0.05 (5%)
- pHitWicket = 0.05 (5%)
Total = 1.00
```

### Modifier Strategy
```
Instead of adding directly to stumping/run_out:
1. Base stumping/run out very low (rare events)
2. Keeper skill: Boost caught_behind + caught (good keepers = better catching)
3. Chase pressure: Only then increase run_out risk significantly
4. Pitch effects: Stumping +0.01-0.02 (not +0.04)
```

This gives realistic distribution while allowing situation modifiers to raise specific types when needed.
