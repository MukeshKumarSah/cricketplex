# Aggressive Batter Wicket Modifier - Technical Analysis

## Base Probabilities (Current)

| Format | pWicket | Boundary (p4+p6) | RPO Target |
|--------|---------|------------------|-----------|
| T20    | 6.5%    | 18%              | 8.0       |
| ODI    | 5.5%    | 8%               | 5.2       |
| FC     | 3.5%    | 6%               | 3.0       |

## Aggressive Modifier Analysis

### Format Scales
- T20: `aggrScale = 1.3`
- ODI: `aggrScale = 0.8`
- FC: `aggrScale = 0.5`

### Boundary Boost (Aggressive Batters)
```
p4 += 0.025 * aggrScale
p6 += 0.02 * aggrScale
```

**Boundary Increase:**
- T20: (0.025 × 1.3) + (0.02 × 1.3) = 0.0585 → **+32.5% more boundaries** (0.18 → 0.2385)
- ODI: (0.025 × 0.8) + (0.02 × 0.8) = 0.036 → **+45% more boundaries** (0.08 → 0.116)
- FC: (0.025 × 0.5) + (0.02 × 0.5) = 0.0225 → **+37.5% more boundaries** (0.06 → 0.0825)

---

## Wicket Rate Comparison (Per Ball Faced)

### Scenario: Typical Aggressive Batter

#### T20 Match (25 balls faced on average)
| Modifier | pWicket | Total Wickets | % vs Baseline |
|----------|---------|---------------|---------------|
| Baseline | 6.5%    | 1.63          | 100%          |
| **0.018** (Old)   | **8.84%**   | **2.21**      | **+35.5%**    |
| **0.008** (Current)   | **7.54%**   | **1.89**      | **+15.9%**    |
| **0.012** (Proposed)   | **8.06%**   | **2.01**      | **+23.3%**    |

#### ODI Match (50 balls faced on average)
| Modifier | pWicket | Total Wickets | % vs Baseline |
|----------|---------|---------------|---------------|
| Baseline | 5.5%    | 2.75          | 100%          |
| **0.018** (Old)   | **6.94%**   | **3.47**      | **+26.2%**    |
| **0.008** (Current)   | **6.14%**   | **3.07**      | **+11.6%**    |
| **0.012** (Proposed)   | **6.46%**   | **3.23**      | **+17.5%**    |

#### FC Match (70 balls faced on average)
| Modifier | pWicket | Total Wickets | % vs Baseline |
|----------|---------|---------------|---------------|
| Baseline | 3.5%    | 2.45          | 100%          |
| **0.018** (Old)   | **4.4%**    | **3.08**      | **+25.7%**    |
| **0.008** (Current)   | **3.9%**    | **2.73**      | **+11.5%**    |
| **0.012** (Proposed)   | **4.1%**    | **2.87**      | **+17.1%**    |

---

## Real Match Impact (per innings)

### T20 Innings (Typical lineup: 2-3 aggressive batters)
| Scenario | Total Aggressive Wickets | Tail Collapse Risk |
|----------|-------------------------|-------------------|
| Baseline (no aggr) | 0 | Low |
| **0.018** (Old)   | **~4-7 wickets**    | **HIGH** (cascading) |
| **0.008** (Current)   | **~2-3 wickets**    | **Very Low** (Maybe too low?) |
| **0.012** (Proposed)   | **~3-4 wickets**    | **Moderate** (Realistic) |

### ODI Innings (2-3 aggressive batters, balanced lineup)
| Scenario | Aggressive Wickets | Normal Wickets | Collapse Risk |
|----------|-------------------|-----------------|---------------|
| Baseline | 0 | ~5-6 | Low |
| **0.018** (Old)   | **~1-1.5**    | **~5-6**    | **HIGH** (all-out by over 43) |
| **0.008** (Current)   | **~0.6-0.9**  | **~5-6**    | **Very Low** |
| **0.012** (Proposed)   | **~0.9-1.4**  | **~5-6**    | **Moderate** |

---

## Boundary vs Wicket Ratio (Realistic Test)

### What's Realistic?
In real cricket, if a batter increases boundaries by +30-40%, they might realistically increase wickets by:
- **Conservative aggressive batter:** +15-20% wickets
- **High-risk aggressive batter:** +25-30% wickets

### Your Current Values:
- **0.008:** +15.9% (T20), +11.6% (ODI), +11.5% (FC) → **Slightly conservative**
- **0.012:** +23.3% (T20), +17.5% (ODI), +17.1% (FC) → **Realistic balanced**
- **0.018:** +35.5% (T20), +26.2% (ODI), +25.7% (FC) → **Too punishing**

---

## Recommendation

**Use 0.012 for aggressive batter wicket modifier:**
- ✅ Aggressive batters are 32-45% more likely to hit boundaries
- ✅ Aggressive batters are 17-23% more likely to get out (realistic proportionality)
- ✅ Enough to differentiate from baseline (11-15% increase vs 0.008)
- ✅ Prevents unrealistic all-outs (much lower than 0.018's 25-35% increase)
- ✅ Feels "real" — aggressive scoring with calculated risk, not reckless dying

---

## Also Check: Bowling Aggression

Current: `pWicket += 0.010 * aggrScale`

This affects same batters when facing aggressive bowlers.

**Combined Effect (Aggressive Batter vs Aggressive Bowler):**
- Aggressive batter: +0.012 × aggrScale
- Aggressive bowler: +0.010 × aggrScale
- Total additive: +0.022 × aggrScale

For ODI: (0.012 + 0.010) × 0.8 = 0.0176 → 5.5% + 1.76% = 7.26%

That's reasonable—more challenging matchup but not stacked unrealistically.
