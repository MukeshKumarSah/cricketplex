import { useState } from 'react';
import {
  HiOutlineCalculator,
  HiOutlineChevronDown,
  HiOutlineChevronRight,
  HiOutlineCurrencyDollar,
  HiOutlineBolt,
  HiOutlineAcademicCap,
  HiOutlineUserGroup,
  HiOutlineCloud,
  HiOutlineTrophy,
  HiOutlineStar,
  HiOutlineHeart,
  HiOutlineScale,
  HiOutlineUser,
  HiOutlineTicket,
} from 'react-icons/hi2';
import './GameFormulas.css';

const sections = [
  {
    id: 'salary',
    icon: HiOutlineCurrencyDollar,
    title: 'Salary & Finances',
    subsections: [
      {
        title: 'Starting Funds',
        rows: [
          ['New Team Balance', '$50,000'],
        ],
      },
      {
        title: 'Player Wages (per day)',
        rows: [
          ['Squad Generation', '$300 – $800'],
          ['Academy Pull (Youth)', '$100 – $300'],
          ['Default Wage', '$500'],
        ],
      },
      {
        title: 'Transfer Market Fees',
        rows: [
          ['Market Value', 'Max(rating × 1,500 × ageFactor, $5,000)'],
          ['Listing Fee', '20% of Market Value (upfront, non-refundable)'],
          ['Total Tax on Sale', 'Max(5% of Sold Price, $5,000)'],
          ['Tax Settlement', 'listingFee − totalTax (refund if +, charge if −)'],
          ['Min Bid', '>= Market Value'],
        ],
      },
      {
        title: 'Market Value Age Factor',
        rows: [
          ['Age ≤ 19', '1.5×'],
          ['Age 20 – 24', '1.3×'],
          ['Age 25 – 29', '1.0×'],
          ['Age 30 – 33', '0.7×'],
          ['Age 34+', '0.4×'],
        ],
      },
      {
        title: 'Academy Upgrade Costs',
        rows: [
          ['Level 1 → 2', '$5,000'],
          ['Level 2 → 3', '$15,000'],
          ['Level 3 → 4', '$40,000'],
          ['Max Level', '4'],
        ],
      },
    ],
  },
  {
    id: 'gate-money',
    icon: HiOutlineTicket,
    title: 'Match Gate Money (League Only)',
    subsections: [
      {
        title: 'Ticket Prices',
        header: ['Category', 'Price', 'Preference %'],
        grid: [
          ['Standing', '$2', '40% of fans prefer this'],
          ['Economy', '$4', '30% of fans prefer this'],
          ['Standard', '$7', '20% of fans prefer this'],
          ['Premium', '$10', '10% of fans prefer this'],
        ],
        note: 'People prefer cheap seats. When a tier fills up, only 25% of overflow fans are willing to upgrade to the next tier — the rest leave.',
      },
      {
        title: 'Stadium Constraints',
        rows: [
          ['Total Capacity Cap', '50,000 seats (hard limit)'],
          ['Ordering Rule', 'Premium ≤ Standard ≤ Economy ≤ Standing'],
          ['Default Layout', 'Standing 3K, Economy 12K, Standard 8K, Premium 2K (25K total)'],
        ],
        note: 'Stadium capacity is only a cap, never a driver. Shrinking cheap sections hurts you — 75% of displaced fans just leave instead of upgrading.',
      },
      {
        title: 'Demand Calculation (how many people WANT to come)',
        rows: [
          ['Fan Base', 'homeFans + awayFans × 0.30 (30% travel factor)'],
          ['Morale Multiplier', '0.70 + (avgMorale / 100) × 0.60 → range 0.70 – 1.30'],
          ['Division Multiplier', 'Div 1: ×1.50 · Div 2: ×1.25 · Div 3: ×1.00 · Div 4+: ×0.85'],
          ['Rating Multiplier', 'Based on avg ELO of both teams → range 0.70 – 1.40'],
          ['Random Factor', '±15% per match (0.85 – 1.15) for variety'],
          ['Total Demand', 'fanBase × morale × division × rating × random'],
        ],
        note: 'Fans, morale, division, and team quality all drive demand. Stadium size never inflates attendance.',
      },
      {
        title: 'Seat Distribution (cheapest fills first)',
        rows: [
          ['Step 1', '40% of demand wants Standing → fill up to capacity'],
          ['Step 2', 'Standing overflow → 25% willing to upgrade to Economy, 75% leave'],
          ['Step 3', 'Economy overflow → 25% upgrade to Standard, 75% leave'],
          ['Step 4', 'Standard overflow → 25% upgrade to Premium, 75% leave'],
          ['Step 5', 'Premium overflow → turned away'],
        ],
        note: 'This cascade prevents gaming. If you shrink Standing to 100 seats, most of those fans just leave instead of paying double for Economy.',
      },
      {
        title: 'Revenue Split (Home vs Away)',
        rows: [
          ['Home Share', '60% + (homeFanRatio × 5%) → 60–65%'],
          ['Away Share', '35–40% (remainder)'],
          ['Fan Ratio', 'homeFans / (homeFans + awayFans)'],
        ],
        note: 'Teams with larger fanbases earn a slightly larger home share. Equal fanbases → 62.5% home, 37.5% away.',
      },
      {
        title: 'Example: 3K fans, Div 1, Rating 1550, Morale 100 vs 3K fans, Rating 1000, Morale 50',
        rows: [
          ['Fan Base', '3,000 + 3,000 × 0.30 = 3,900'],
          ['Morale (avg 75)', '×1.15'],
          ['Division 1', '×1.50'],
          ['Rating (avg 1275)', '×1.12'],
          ['Total Demand', '~7,515 people'],
          ['Standing (40%)', '3,006 want → 3,000 cap → 3,000 seated, 6 overflow → 1 upgrades'],
          ['Economy (30%)', '2,255 want → 12,000 cap → 2,255 seated'],
          ['Standard (20%)', '1,503 want → 8,000 cap → 1,503 seated'],
          ['Premium (10%)', '752 want → 2,000 cap → 752 seated'],
          ['Total Attendance', '~7,510 / 25,000 (30% fill)'],
          ['Revenue', '$2×3K + $4×2.3K + $7×1.5K + $10×752 = ~$33,000'],
        ],
        note: 'With only 3K fans, even top-of-league Div 1 with max morale fills ~30%. As fan count grows (toward 150K ceiling), stadiums sell out.',
      },
      {
        title: 'Multiplier Reference',
        header: ['Factor', 'Low', 'Mid', 'High'],
        grid: [
          ['Morale', '0 → ×0.70', '50 → ×1.00', '100 → ×1.30'],
          ['Division', 'Div 4+ → ×0.85', 'Div 3 → ×1.00', 'Div 1 → ×1.50'],
          ['Rating (avg ELO)', '800 → ×0.85', '1000 → ×0.96', '1400+ → ×1.40'],
          ['Random', '×0.85', '×1.00', '×1.15'],
        ],
      },
    ],
  },
  {
    id: 'ratings',
    icon: HiOutlineStar,
    title: 'Player Ratings',
    subsections: [
      {
        title: 'Overall Rating Formula (by Role)',
        rows: [
          ['Batsman', '(BAT × 0.55) + (BOWL × 0.10) + (FLD × 0.35)'],
          ['Bowler', '(BAT × 0.10) + (BOWL × 0.55) + (FLD × 0.35)'],
          ['All-Rounder', '(BAT × 0.35) + (BOWL × 0.35) + (FLD × 0.30)'],
          ['Keeper', '(BAT × 0.30) + (BOWL × 0.05) + (WK × 0.35) + (FLD × 0.30)'],
        ],
      },
      {
        title: 'Squad Generation Ratings (New Team)',
        rows: [
          ['Batsman BAT', '25 – 35'],
          ['Batsman BOWL', '0 – 15'],
          ['Bowler BOWL', '25 – 35'],
          ['Bowler BAT', '0 – 15'],
          ['All-Rounder BAT/BOWL', '20 – 30 each'],
          ['Keeper BAT', '25 – 33'],
          ['Keeper WK', '20 – 30'],
          ['All FLD', '10 – 35'],
          ['Confidence', '40 – 65'],
        ],
      },
      {
        title: 'Academy Pull Ratings (Age 17)',
        rows: [
          ['Batsman BAT', '8 – 18'],
          ['Bowler BOWL', '8 – 18'],
          ['All-Rounder BAT/BOWL', '6 – 14 each'],
          ['Keeper BAT', '8 – 16'],
          ['Keeper WK', '8 – 18'],
          ['All FLD', '5 – 15'],
          ['Confidence', '30 – 50'],
        ],
      },
    ],
  },
  {
    id: 'pitch',
    icon: HiOutlineBolt,
    title: 'Pitch Effects on Bowlers',
    subsections: [
      {
        title: 'Bowler Bonus by Pitch Type',
        header: ['Pitch', 'F', 'FM', 'MF', 'M', 'FS', 'WS'],
        grid: [
          ['Green', '+14', '+9', '+10', '+12', '+2', '+1'],
          ['Dusty', '+2', '+4', '+4', '+5', '+14', '+12'],
          ['Flat', '−5', '−6', '−6', '−7', '−5', '−4'],
          ['Bouncy', '+12', '+8', '+7', '+4', '+1', '0'],
          ['Uneven', '+10', '+8', '+9', '+6', '+6', '+7'],
          ['Dry', '+1', '+2', '+4', '+5', '+10', '+12'],
          ['Slow', '−4', '−2', '0', '+2', '+7', '+5'],
          ['Standard', '0', '0', '0', '0', '0', '0'],
        ],
      },
      {
        title: 'Batter Pitch Modifiers',
        rows: [
          ['Green', 'LH: +3, then +skillAdapt×0.5 + expAdapt×0.5'],
          ['Dusty', 'LH: −4, then +skillAdapt + expAdapt×0.5'],
          ['Flat', '+4 + skillAdapt×1.5'],
          ['Bouncy', 'LH: −2, RH: −1, then +skillAdapt + expAdapt'],
          ['Uneven', '−2 + skillAdapt + expAdapt'],
          ['Dry', 'LH: −3, then +skillAdapt×0.8 + expAdapt×0.5'],
          ['Slow', '+2 + skillAdapt'],
          ['Standard', '0 (neutral)'],
        ],
        note: 'skillAdapt: BAT 60+ → 2.0, BAT 40+ → 1.0, else 0. expAdapt: EXP 15+ → 2.0, EXP 8+ → 1.0, else −1.0',
      },
    ],
  },
  {
    id: 'weather',
    icon: HiOutlineCloud,
    title: 'Weather Effects',
    subsections: [
      {
        title: 'Global Batting Modifier by Weather',
        header: ['Weather', 'BAT mod'],
        grid: [
          ['Sunny', '+3'],
          ['Hot & Humid', '+1'],
          ['Partly Cloudy', '+1'],
          ['Overcast', '−3'],
          ['Light Rain', '−4'],
          ['Heavy Rain', '−6'],
          ['Windy', '−1'],
          ['Foggy', '−5'],
        ],
        note: 'Temperature >38°C → additional −2 to batting.',
      },
      {
        title: 'Per-Batter Weather Modifier (experience & skill scaled)',
        header: ['Weather', 'Base', 'Exp Factor', 'Skill Factor'],
        grid: [
          ['Sunny', '0', '—', '—'],
          ['Hot & Humid', '0', '—', '—'],
          ['Partly Cloudy', '0', '—', '—'],
          ['Overcast', '−1', '+exp×0.8', '+skl×0.5'],
          ['Light Rain', '−2', '+exp×1.0', '+skl×0.5'],
          ['Heavy Rain', '−3', '+exp×1.2', '+skl×0.5'],
          ['Windy', '−1', '+exp×0.3', '+skl×1.0'],
          ['Foggy', '−2', '+exp×1.0', '+skl×0.3'],
        ],
        note: 'Exp factor: ≥15yr → +2.0, ≥8yr → +0.5, <8yr → −1.5. Skill factor: rating ≥50 → +1.0, ≥30 → 0, <30 → −1.0.',
      },
      {
        title: 'Bowling Bonus by Weather',
        header: ['Weather', 'F', 'FM', 'MF', 'M', 'FS', 'WS'],
        grid: [
          ['Sunny', '−3', '−2', '−1', '−1', '0', '0'],
          ['Overcast', '+10', '+7', '+6', '+5', '+1', '0'],
          ['Light Rain', '+8', '+6', '+5', '+4', '0', '−1'],
          ['Heavy Rain', '+5', '+4', '+3', '+2', '−2', '−3'],
          ['Windy', '+4', '+3', '+2', '+1', '+2', '+3'],
          ['Foggy', '+4', '+3', '+2', '+1', '+3', '+3'],
        ],
        note: 'Temperature: >38°C → bowling −1. <12°C → bowling −1.',
      },
    ],
  },
  {
    id: 'matchengine',
    icon: HiOutlineTrophy,
    title: 'Match Engine — Delivery Outcomes',
    subsections: [
      {
        title: 'Base Probabilities by Format',
        header: ['Outcome', 'T20', 'ODI', 'FC'],
        grid: [
          ['Dot', '30%', '40%', '48%'],
          ['Single', '24%', '28%', '25%'],
          ['Double', '10%', '10%', '10%'],
          ['Triple', '2%', '3%', '4%'],
          ['Four', '15%', '8%', '6%'],
          ['Six', '8%', '3%', '1%'],
          ['Wicket', '5.5%', '4%', '3%'],
          ['~RPO', '~8.0', '~5.2', '~3.0'],
        ],
      },
      {
        title: 'Skill Difference Shift',
        rows: [
          ['Formula', 'shift = (batStrength − bowlStrength) / 200 × formatScale'],
          ['Format Scale', 'T20: 1.3, ODI: 1.0, FC: 0.7'],
          ['Dot → shift', '−shift × 0.4'],
          ['Singles → shift', '+shift × 0.12'],
          ['Doubles → shift', '+shift × 0.06'],
          ['Fours → shift', '+shift × 0.12'],
          ['Sixes → shift', '+shift × 0.08'],
          ['Wicket → shift', '−shift × 0.35'],
        ],
      },
      {
        title: 'Aggression Modifiers (× aggrScale)',
        header: ['Stat', 'Aggressive', 'Defensive'],
        grid: [
          ['Fours', '+0.03', '−0.02'],
          ['Sixes', '+0.035', '−0.02'],
          ['Wicket', '+0.025', '−0.02'],
          ['Dots', '−0.06', '+0.04'],
          ['Singles', '−0.025', '+0.02'],
        ],
        note: 'aggrScale: T20 = 1.3, ODI = 0.9, FC = 0.6',
      },
      {
        title: 'Extras — Wides & No-Balls',
        rows: [
          ['T20 Base', '4% extra chance'],
          ['ODI Base', '2.5% extra chance'],
          ['FC Base', '1.5% extra chance'],
          ['Modifiers', '+1% bowler aggression, −0.02%/rating pt, +1% fatigue'],
          ['Clamped', '1% – 10%'],
          ['Split', '70% wide, 30% no-ball'],
        ],
      },
      {
        title: 'Phase Modifiers (batting boost/penalty)',
        header: ['Phase', 'T20', 'ODI', 'FC'],
        grid: [
          ['Powerplay', 'Ov 1–6: +3', 'Ov 1–10: +1.5', 'Session ball 1–10: +1.0'],
          ['Middle', 'Ov 7–15: −1', 'Ov 11–30: −2', 'Ball 11–25: −2.5'],
          ['Buildup', '—', 'Ov 31–40: +1', 'Ball 26–40: −0.5'],
          ['Death', 'Ov 16–20: +5', 'Ov 41–50: +3.5', 'Ball 41–50: +1.5'],
        ],
      },
    ],
  },
  {
    id: 'composite',
    icon: HiOutlineCalculator,
    title: 'Composite Strength Calculations',
    subsections: [
      {
        title: 'Batting Composite Strength',
        rows: [
          ['Base', 'batRating'],
          ['+ Aggression', 'aggrMod × 8.0  (A: +1.5, N: 0, D: −1.0)'],
          ['+ Confidence', 'confidence / 100 × 6.0'],
          ['+ Experience', 'Min(experience × 0.3, 10)'],
          ['+ Fitness', 'fitness / 100 × 3.0'],
          ['+ Pitch Bat Mod', 'varies by pitch & handedness'],
          ['− Pitch Bowl Bonus', 'bowlerBonus × 0.5'],
          ['+ Weather', 'battingMod + batter weather mod'],
          ['+ Phase', 'phaseModifier × 2.0'],
          ['× Fatigue', '1.0 − (ballsFaced × 0.001 × (1 − stamina))'],
          ['Clamped', '5 – 120'],
        ],
      },
      {
        title: 'Bowling Composite Strength',
        rows: [
          ['Base', 'bowlRating'],
          ['+ Aggression', 'aggrMod × 5.0'],
          ['+ Confidence', 'confidence / 100 × 5.0'],
          ['+ Experience', 'Min(experience × 0.3, 10)'],
          ['+ Fitness', 'fitness / 100 × 3.0'],
          ['+ Pitch Bonus', 'bowlerBonus by type & pitch'],
          ['+ Weather', 'bowlingMod + type weather mod'],
          ['+ Type Matchup', 'pace vs spin on pitch'],
          ['+ Fielding Avg', 'teamFieldingAvg × 0.15'],
          ['+ Keeper', 'keeperSkill × 0.05'],
          ['× Fatigue', '1.0 − (ballsBowled × 0.001 × (1 − stamina))'],
          ['Clamped', '5 – 120'],
        ],
      },
    ],
  },
  {
    id: 'dismissal',
    icon: HiOutlineBolt,
    title: 'Dismissal & Collapse Mechanics',
    subsections: [
      {
        title: 'Base Dismissal Probabilities',
        header: ['Type', 'Pace', 'Spin', 'Default'],
        grid: [
          ['Bowled', '22%', '18%', '20%'],
          ['Caught', '40%', '40%', '40%'],
          ['LBW', '15%', '22%', '15%'],
          ['Stumped', '2%', '10%', '2%'],
          ['Run Out', '8%', '8%', '8%'],
          ['Caught Behind', '10%', '5%', '5%'],
          ['Hit Wicket', '2%', '2%', '2%'],
        ],
        note: 'Modifiers: +fielding avg×0.002 to Caught, +keeper×0.001 to C&B, +keeper×0.0015 to Stumped. GREEN/BOUNCY: +0.04 C&B, +0.03 Bowled. DUSTY/DRY: +0.04 Stumped, +0.03 LBW.',
      },
      {
        title: 'Wicket Cluster / Collapse',
        rows: [
          ['Collapse Threshold', 'T20: 0.65 wkt/ov, ODI: 0.50, FC: 0.40'],
          ['7+ wickets down', 'collapseFactor = 0.8'],
          ['5+ wickets down', 'collapseFactor = 0.5'],
          ['Aggressive resist', '× 0.55'],
          ['Defensive amplify', '× 1.3'],
          ['Dot increase', '+0.08 × collapse'],
          ['Boundary decrease', '−0.05 (4s), −0.04 (6s)'],
        ],
      },
      {
        title: 'Bye / Leg Bye',
        rows: [
          ['Bye Chance', '0.015 − keeper×0.0001  (min 0.002)'],
          ['Split', '70% bye, 30% leg bye'],
          ['Runs', '70% → 1 run, 30% → 2 runs'],
        ],
      },
    ],
  },
  {
    id: 'chase',
    icon: HiOutlineTrophy,
    title: 'Chase Pressure',
    subsections: [
      {
        title: 'Chase Pressure by Required Run Rate',
        header: ['Req. Rate', 'T20', 'ODI', 'FC'],
        grid: [
          ['Very High', '>14 → +10', '>10 → +10', '>6 → +10'],
          ['High', '>12 → +7', '>8 → +7', '>5 → +6'],
          ['Above Avg', '>10 → +4', '>6 → +4', '>4 → +3'],
          ['Moderate', '>8 → +1', '>5 → +1', '>3 → +1'],
          ['Comfortable', '<5 → −3', '<3 → −3', '<1.5 → −3'],
          ['Cruising', '<3 → −5', '<2 → −5', '<1 → −5'],
        ],
        note: 'Thresholds are applied to the pitch-adjusted RRR (raw RRR × pitch scale).',
      },
      {
        title: 'Pitch RRR Scaling (adjusts perceived chase difficulty)',
        header: ['Pitch', 'RRR Scale', 'Example: raw 10 RRR →'],
        grid: [
          ['Dusty', '×1.25', '12.5 (triggers +7 in T20)'],
          ['Dry', '×1.20', '12.0 (triggers +7 in T20)'],
          ['Green', '×1.15', '11.5 (triggers +4 in T20)'],
          ['Uneven', '×1.15', '11.5 (triggers +4 in T20)'],
          ['Bouncy', '×1.10', '11.0 (triggers +4 in T20)'],
          ['Slow', '×1.10', '11.0 (triggers +4 in T20)'],
          ['Flat', '×0.85', '8.5 (no pressure in T20)'],
          ['Standard', '×1.00', '10.0 (triggers +4 in T20)'],
        ],
        note: 'Effect: bowlStrength += chasePressure, batStrength −= chasePressure × 0.6.',
      },
    ],
  },
  {
    id: 'training',
    icon: HiOutlineAcademicCap,
    title: 'Training Mechanics',
    subsections: [
      {
        title: 'Focused Training Gains (per session)',
        header: ['Type', 'Primary', 'Secondary'],
        grid: [
          ['BAT', 'batRating +1–3', 'stamina +0–2, conf +0–2'],
          ['BOWL', 'bowlRating +1–3', 'stamina +0–2, conf +0–2'],
          ['AR', 'bat +1–2, bowl +1–2', 'conf +0–2, stamina +0–1'],
          ['FLD', 'fldRating +1–3', 'stamina +0–2, conf +0–2'],
          ['WK', 'keeperRating +1–3', 'stamina +0–2, conf +0–2'],
          ['STAMINA', 'stamina +1–3', 'conf +0–2'],
          ['MENTAL', 'confidence +1–3', '—'],
        ],
      },
      {
        title: 'General Training (~40% of focused)',
        header: ['Role', 'Gains'],
        grid: [
          ['Batsman', 'bat +0–1, fld +0–1, stm +0–1, 30% bowl +0–1'],
          ['Bowler', 'bowl +0–1, fld +0–1, stm +0–1, 30% bat +0–1'],
          ['All-Rounder', 'bat +0–1, bowl +0–1, fld +0–1, stm +0–1'],
          ['Keeper', 'wk +0–1, bat +0–1, fld +0–1, stm +0–1'],
        ],
      },
      {
        title: 'Focused Training Slots by Academy Level',
        rows: [
          ['Level 1', '0 slots'],
          ['Level 2', '3 slots'],
          ['Level 3', '5 slots'],
          ['Level 4', '7 slots'],
          ['Level 5', '10 slots'],
        ],
      },
    ],
  },
  {
    id: 'motm',
    icon: HiOutlineStar,
    title: 'Man of the Match Scoring',
    subsections: [
      {
        title: 'Batting Points',
        rows: [
          ['Per Run', '1.0 pt'],
          ['Per Four', '+1.5 pts'],
          ['Per Six', '+2.0 pts'],
          ['Half-Century (50+)', '+15 bonus'],
          ['Century (100+)', '+30 bonus'],
        ],
      },
      {
        title: 'Bowling Points',
        rows: [
          ['Per Wicket', '20.0 pts'],
          ['Per Maiden', '+5.0 pts'],
          ['Per Dot Ball', '+0.5 pts'],
          ['3-Wicket Haul', '+15 bonus'],
          ['5-Wicket Haul', '+30 bonus'],
          ['Economy < 5.0 (1+ ov)', '+10 bonus'],
        ],
      },
    ],
  },
  {
    id: 'bots',
    icon: HiOutlineUserGroup,
    title: 'Bot Team Generation',
    subsections: [
      {
        title: 'Division-Based Ratings',
        header: ['Division', 'Base', 'Variance', 'Range'],
        grid: [
          ['Div 1', '1100', '±75', '1025 – 1175'],
          ['Div 2', '900', '±75', '825 – 975'],
          ['Div 3+', '750', '±75', '675 – 825'],
        ],
      },
      {
        title: 'Bot Fans / Popularity',
        rows: [
          ['Division 1', '5,000 – 9,999'],
          ['Division 2+', '1,000 – 3,999'],
        ],
      },
      {
        title: 'Toss Decision Logic (Bot AI)',
        rows: [
          ['Flat pitch', 'batScore +3'],
          ['Green / Bouncy', 'batScore −3'],
          ['Dusty / Dry', 'batScore −1'],
          ['Uneven', 'batScore −2'],
          ['Overcast / Rain', 'batScore −2'],
          ['Sunny', 'batScore +1'],
          ['Random jitter', '−1 to +2'],
          ['Decision', 'batScore ≥ 0 → BAT first, else BOWL'],
        ],
      },
    ],
  },
  {
    id: 'morale-fans',
    icon: HiOutlineHeart,
    title: 'Team Morale & Fans',
    subsections: [
      {
        title: 'Team Morale (0–100, persistent)',
        rows: [
          ['Default', '50 (starting value for new teams)'],
          ['Updated', 'After every match — three factors combined'],
          ['Match Result (primary)', 'Win +8, Draw/Tie/NR +2, Loss −6'],
          ['Squad Confidence (15%)', 'Nudges morale toward avg squad confidence: (avgConf − morale) × 0.15'],
          ['Academy Level (10%)', 'Nudges morale toward academy benchmark: (level×25 − morale) × 0.10'],
          ['Formula', 'new = current + resultDelta + squadPull + academyPull, clamped 0–100'],
        ],
        note: 'Match results are the primary driver. Squad confidence and academy level gently pull morale toward their benchmarks — high-confidence squads recover faster; better facilities provide a small floor.',
      },
      {
        title: 'Morale — Example (squad confidence 70, academy Lv 2)',
        header: ['Event', 'Before', 'Result Δ', 'Squad Pull', 'Academy Pull', 'After'],
        grid: [
          ['Win', '50', '+8', '+3.0', '+0.0', '61'],
          ['Win', '61', '+8', '+1.4', '−1.1', '69'],
          ['Loss', '69', '−6', '+0.2', '−1.9', '61'],
          ['Loss', '61', '−6', '+1.4', '−1.1', '55'],
          ['Draw', '55', '+2', '+2.3', '−0.5', '59'],
        ],
        note: 'Squad pull is positive when confidence > morale (helps recovery). Academy pull is negative when morale exceeds the academy benchmark (Lv 2 = 50).',
      },
      {
        title: 'Squad Confidence Factor (15% weight)',
        rows: [
          ['Source', 'Average confidence of all squad players (0–100)'],
          ['No players', 'Defaults to 50'],
          ['Effect', 'Pulls morale toward squad confidence each match'],
        ],
      },
      {
        title: 'Academy Level Factor (10% weight)',
        rows: [
          ['Benchmark', 'Academy Level × 25'],
          ['Level 1', '25 → very small floor'],
          ['Level 2', '50 → neutral benchmark'],
          ['Level 3', '75 → helps sustain high morale'],
          ['Level 4', '100 → strong upward pull'],
        ],
        note: 'Academy is the smallest factor — it provides a gentle floor or ceiling, not a primary driver.',
      },
      {
        title: 'Morale Labels',
        header: ['Range', 'Label', 'Color'],
        grid: [
          ['90–100', 'Ecstatic', 'Cyan'],
          ['75–89', 'Happy', 'Green'],
          ['55–74', 'Content', 'Yellow'],
          ['35–54', 'Unsettled', 'Orange'],
          ['0–34', 'Low', 'Red'],
        ],
      },
      {
        title: 'Fan Growth (per match, last 10)',
        rows: [
          ['Ceiling', '150,000 (hard cap)'],
          ['Ratio', 'fans ÷ 150,000 (0.0 – 1.0)'],
        ],
      },
      {
        title: 'Fan Gain (exposure — diminishes toward ceiling)',
        header: ['Outcome', 'Base Gain', 'Minimum'],
        grid: [
          ['Win', 'floor(500 × (1 − ratio))', '20'],
          ['Draw / Tie / NR', 'floor(200 × (1 − ratio))', '10'],
          ['Loss', 'floor(100 × (1 − ratio))', '5'],
        ],
      },
      {
        title: 'Fan Penalty (attrition — quadratic, hurts more at high fans)',
        header: ['Outcome', 'Penalty'],
        grid: [
          ['Win', 'None (pure gain)'],
          ['Draw / Tie / NR', 'floor(fans × 0.001 × ratio)'],
          ['Loss', 'floor(fans × 0.004 × ratio)'],
        ],
        note: 'Net change = Gain − Penalty. Wins always grow. Losses break even ~50K fans, go negative above.',
      },
      {
        title: 'Fan Examples',
        header: ['Fans', 'Win', 'Draw', 'Loss'],
        grid: [
          ['1K', '+500', '+200', '+100'],
          ['25K', '+417', '+142', '+58'],
          ['50K', '+334', '+84', '−0 (break-even)'],
          ['80K', '+234', '+51', '−124'],
          ['100K', '+167', '−0', '−233'],
          ['130K', '+67', '−86', '−438'],
        ],
        note: 'Equilibrium for an average team (5W/2D/3L per 10 matches) is ~100K fans.',
      },
    ],
  },
  {
    id: 'team-elo',
    icon: HiOutlineScale,
    title: 'Team Elo Ratings',
    subsections: [
      {
        title: 'Elo System Overview',
        rows: [
          ['Type', 'Standard Elo with K-factor 20'],
          ['Default Rating', '1000 (new teams)'],
          ['Minimum', '100 (floor)'],
          ['Per-Format', 'Separate ratings for ODI, T20, and FC'],
          ['Updated', 'After every completed match'],
        ],
      },
      {
        title: 'Elo Formula',
        rows: [
          ['Expected Score', 'E_A = 1 / (1 + 10^((R_B − R_A) / 400))'],
          ['New Rating', 'R_A\' = R_A + K × (S_A − E_A)'],
          ['K-Factor', '20'],
          ['Win Score', 'S = 1.0'],
          ['Draw / Tie / NR Score', 'S = 0.5'],
          ['Loss Score', 'S = 0.0'],
        ],
        note: 'Both teams are updated simultaneously. Rating points are zero-sum — what one team gains, the other loses.',
      },
      {
        title: 'Elo Examples (K = 20)',
        header: ['Match', 'Winner Rating', 'Loser Rating', 'Winner Δ', 'Loser Δ'],
        grid: [
          ['Equal teams', '1000', '1000', '+10', '−10'],
          ['Favorite wins', '1200', '1000', '+5', '−5'],
          ['Upset', '1000', '1200', '+15', '−15'],
          ['Big upset', '800', '1200', '+18', '−18'],
          ['Draw (equal)', '1000', '1000', '0', '0'],
          ['Draw (mismatch)', '1200', '1000', '−5', '+5'],
        ],
        note: 'Upsets produce larger swings. Draws favor the lower-rated team.',
      },
    ],
  },
  {
    id: 'player-fitness-confidence',
    icon: HiOutlineUser,
    title: 'Player Fitness & Confidence (Post-Match)',
    subsections: [
      {
        title: 'Fitness Loss Overview',
        rows: [
          ['When', 'After every match, all playing XI players lose fitness'],
          ['Range', '0 – 100 (clamped)'],
          ['Recovery', 'Fitness recovers between matches (rest days)'],
        ],
      },
      {
        title: 'Base Fitness Loss by Format',
        header: ['Format', 'Base Loss', 'Bat Divisor', 'Bowl Divisor'],
        grid: [
          ['T20', '−3', '40', '1.5'],
          ['ODI', '−5', '60', '3'],
          ['FC', '−8', '80', '5'],
        ],
        note: 'Heavier formats = more base fatigue.',
      },
      {
        title: 'Fitness Loss Formula',
        rows: [
          ['Batting load', 'ballsFaced ÷ batDivisor'],
          ['Bowling load', 'oversBowled ÷ bowlDivisor'],
          ['Raw loss', 'baseLoss + batLoad + bowlLoad'],
          ['Stamina multiplier', '1.3 − (stamina / 100) × 0.6'],
          ['Final loss', 'round(rawLoss × staminaMulti)'],
        ],
        note: 'High stamina (100) = ×0.7 multiplier. Low stamina (0) = ×1.3 multiplier. Stamina reduces fatigue impact.',
      },
      {
        title: 'Fitness Loss Examples (ODI, base −5)',
        header: ['Player', 'Balls Faced', 'Overs Bowled', 'Stamina', 'Fitness Loss'],
        grid: [
          ['Batter (80 balls)', '80', '0', '70', '−5 (base) −1.3 (bat) = ~−5'],
          ['Bowler (10 overs)', '0', '10', '60', '−5 (base) −3.3 (bowl) = ~−8'],
          ['All-rounder (40b, 8ov)', '40', '8', '50', '−5 −0.7 −2.7 = ~−9'],
          ['Low stamina bowler', '0', '10', '20', '−5 −3.3 = ~−10 (×1.18)'],
          ['High stamina batter', '120', '0', '95', '−5 −2.0 = ~−5 (×0.73)'],
        ],
      },
      {
        title: 'Confidence Change Overview',
        rows: [
          ['When', 'After every match, based on individual performance'],
          ['Range', '0 – 100 (clamped)'],
          ['Factors', 'Batting runs, bowling wickets/economy, MoM, win/loss'],
          ['Format-aware', 'Thresholds differ for T20, ODI, and FC'],
        ],
      },
      {
        title: 'Batting Confidence (format-specific thresholds)',
        header: ['Performance', 'T20', 'ODI', 'FC'],
        grid: [
          ['Duck (0 runs, dismissed)', '−4', '−4', '−4'],
          ['Low score (no duck)', '< 15 → −1', '< 20 → −1', '< 25 → −1'],
          ['Decent knock', '25+ → +2', '35+ → +2', '50+ → +2'],
          ['Good knock', '40+ → +4', '50+ → +4', '75+ → +4'],
          ['Elite knock', '75+ → +7', '100+ → +7', '150+ → +7'],
        ],
        note: 'In FC matches, runs and ducks are aggregated across both innings. A 40+30 counts as 70 total.',
      },
      {
        title: 'Bowling Confidence (format-specific economy)',
        header: ['Performance', 'T20', 'ODI', 'FC'],
        grid: [
          ['0 wkts & expensive', '> 10 econ → −3', '> 7 econ → −3', '> 4 econ → −3'],
          ['1 wicket', '+1', '+1', '+1'],
          ['2 wickets', '+2', '+2', '+2'],
          ['3 – 4 wickets', '+4', '+4', '+4'],
          ['5+ wickets', '+7', '+7', '+7'],
        ],
        note: 'In FC matches, wickets and economy are aggregated across all bowled innings.',
      },
      {
        title: 'Bonus & Team Result',
        header: ['Factor', 'Δ Confidence'],
        grid: [
          ['Man of the Match', '+5'],
          ['Winning team', '+2'],
          ['Losing team', '−2'],
          ['Draw / Tie / NR', '0'],
        ],
        note: 'All factors stack. A batter scoring 50 on the winning team with MoM gets +4 +5 +2 = +11.',
      },
      {
        title: 'Experience Gain (diminishing returns)',
        rows: [
          ['Raw XP', 'Computed from base + format + performance + MoM (range 1–7)'],
          ['Diminishing formula', 'effectiveXP = floor(rawXP × 50 / (50 + currentXP))'],
          ['Soft cap', '~100 — average performers plateau; only standouts push past'],
          ['No hard cap', 'XP can exceed 100 but gains become very small'],
        ],
        note: 'Early career: full gains. Mid career: halved. Veterans: only standout performances matter.',
      },
      {
        title: 'Diminishing Returns — Multiplier by Current XP',
        header: ['Current XP', 'Multiplier', 'Raw 2 → Effective', 'Raw 5 → Effective'],
        grid: [
          ['0', '×1.00', '2', '5'],
          ['10', '×0.83', '1', '4'],
          ['25', '×0.67', '1', '3'],
          ['50', '×0.50', '1', '2'],
          ['75', '×0.40', '0', '2'],
          ['100', '×0.33', '0', '1'],
          ['150', '×0.25', '0', '1'],
        ],
        note: 'Average players (raw ~2) stop gaining around XP 50–60. Stars (raw ~5) keep climbing slowly past 100.',
      },
      {
        title: 'Raw XP Sources',
        rows: [
          ['Base', '+1 per match played'],
          ['FC bonus', '+1 extra (longer format)'],
        ],
      },
      {
        title: 'Experience — Batting Performance Bonus',
        header: ['Level', 'T20', 'ODI', 'FC'],
        grid: [
          ['Good knock', '40+ → +1', '50+ → +1', '75+ → +1'],
          ['Elite knock', '75+ → +2', '100+ → +2', '150+ → +2'],
        ],
      },
      {
        title: 'Experience — Bowling Performance Bonus',
        header: ['Performance', 'Raw XP'],
        grid: [
          ['3 – 4 wickets', '+1'],
          ['5+ wickets', '+2'],
        ],
      },
      {
        title: 'Experience — Other Bonuses',
        header: ['Factor', 'Raw XP'],
        grid: [
          ['Man of the Match', '+1'],
        ],
        note: 'All raw bonuses stack before the diminishing formula is applied.',
      },
      {
        title: 'Season Progression Example (42 matches/season, avg raw 2)',
        header: ['Season', 'Start XP', 'End XP', 'Avg Gain/Match'],
        grid: [
          ['1', '0', '~35', '~0.8'],
          ['2', '35', '~52', '~0.4'],
          ['3', '52', '~62', '~0.2'],
          ['4', '62', '~68', '~0.1'],
          ['5+', '68+', 'Very slow', '0–1'],
        ],
        note: 'Star performers (raw ~5) reach 80 in ~2 seasons, 100+ requires 4+ seasons of dominance.',
      },
      {
        title: 'How Experience Affects Gameplay',
        rows: [
          ['Batting/Bowling strength', 'experience × 0.3, capped at +10 bonus'],
          ['Pitch adaptation', '15+ XP: +2 modifier, 8–14: +1, < 8: −1'],
          ['Weather coping', '15+ XP: +2, 8–14: +0.5, < 8: −1.5'],
        ],
        note: 'Experienced players handle tough conditions and pitches significantly better than newcomers.',
      },
    ],
  },
];

function Section({ section }) {
  const [open, setOpen] = useState(false);
  const Icon = section.icon;

  return (
    <div className={`gf-section ${open ? 'open' : ''}`}>
      <button className="gf-section-toggle" onClick={() => setOpen(!open)}>
        <div className="gf-section-left">
          <Icon className="gf-section-icon" />
          <span className="gf-section-title">{section.title}</span>
        </div>
        {open ? <HiOutlineChevronDown className="gf-chevron" /> : <HiOutlineChevronRight className="gf-chevron" />}
      </button>

      {open && (
        <div className="gf-section-body">
          {section.subsections.map((sub, si) => (
            <div key={si} className="gf-sub">
              <h3 className="gf-sub-title">{sub.title}</h3>

              {sub.grid && (
                <div className="gf-table-wrap">
                  <table className="gf-table">
                    {sub.header && (
                      <thead>
                        <tr>
                          {sub.header.map((h, i) => <th key={i}>{h}</th>)}
                        </tr>
                      </thead>
                    )}
                    <tbody>
                      {sub.grid.map((row, ri) => (
                        <tr key={ri}>
                          {row.map((cell, ci) => (
                            <td key={ci} className={ci === 0 ? 'gf-cell-label' : 'gf-cell-value'}>
                              {cell}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {sub.rows && !sub.grid && (
                <div className="gf-rows">
                  {sub.rows.map(([label, value], ri) => (
                    <div key={ri} className="gf-row">
                      <span className="gf-row-label">{label}</span>
                      <span className="gf-row-value">{value}</span>
                    </div>
                  ))}
                </div>
              )}

              {sub.note && <p className="gf-note">{sub.note}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function GameFormulas() {
  return (
    <div className="gf-page">
      <div className="gf-header">
        <HiOutlineCalculator className="gf-header-icon" />
        <h1 className="gf-page-title">Game Formulas & Mechanics</h1>
      </div>
      <p className="gf-subtitle">
        Complete reference of all calculations, probabilities, and mechanics used in the game engine.
      </p>
      <div className="gf-sections">
        {sections.map((s) => (
          <Section key={s.id} section={s} />
        ))}
      </div>
    </div>
  );
}
