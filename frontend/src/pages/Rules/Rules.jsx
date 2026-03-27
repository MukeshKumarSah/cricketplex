import { useState } from 'react';
import {
  HiOutlineShieldCheck,
  HiOutlineChevronDown,
  HiOutlineChevronUp,
  HiOutlineUserGroup,
  HiOutlineTrophy,
  HiOutlineBanknotes,
  HiOutlineArrowsRightLeft,
  HiOutlineExclamationTriangle,
  HiOutlineScale,
  HiOutlineClipboardDocumentList,
  HiOutlineClock,
  HiOutlineHandRaised,
} from 'react-icons/hi2';
import './Rules.css';

const sections = [
  {
    id: 'general',
    icon: <HiOutlineClipboardDocumentList />,
    title: 'General Rules',
    content: [
      {
        heading: 'Fair Play Policy',
        text: 'CricketPlex is built on fair competition. All managers must play within the rules of the game. Exploiting bugs, using automated scripts, or engaging in any form of cheating will result in penalties ranging from warnings to permanent bans.',
      },
      {
        heading: 'One Team Per Manager',
        text: 'Each manager account may own and operate exactly one team. Creating multiple accounts to gain an unfair advantage (multi-accounting) is strictly prohibited and will result in all associated accounts being suspended.',
      },
      {
        heading: 'Match Integrity',
        text: 'Match-fixing, result manipulation, and collusion with other managers to influence match outcomes are serious offences. All matches are monitored and suspicious patterns will trigger an investigation.',
      },
    ],
  },
  {
    id: 'squad-rules',
    icon: <HiOutlineUserGroup />,
    title: 'Squad Regulations',
    content: [
      {
        heading: 'Squad Size Limits',
        text: 'Your squad must have a minimum of 15 players and a maximum of 30 players at all times. If your squad drops below the minimum (e.g. due to retirements), you will be given a grace period of 7 game-days to sign replacements before penalties apply.',
      },
      {
        heading: 'Playing XI Requirements',
        text: 'Every match lineup must include exactly 11 players. The lineup must contain at least 1 designated wicket-keeper. For limited-overs formats, you must designate exactly 5 bowlers. AI will auto-pick if you do not set a lineup before the deadline.',
      },
      {
        heading: 'Foreign Player Quota',
        text: 'A maximum of 5 overseas (foreign) players may be included in a single match lineup. Your total squad may hold up to 10 foreign players. This encourages development of local talent through the academy system.',
      },
      {
        heading: 'Player Contracts',
        text: 'All players must be under valid contracts. Contracts range from 1 to 5 seasons. When a contract expires, you must re-sign or release the player. Unsigned players become free agents after a 3-day grace period.',
      },
    ],
  },
  {
    id: 'league-rules',
    icon: <HiOutlineTrophy />,
    title: 'League & Match Rules',
    content: [
      {
        heading: 'League Structure',
        text: 'Leagues are divided into tiers: Premier, Championship, and Development. Promotion and relegation occur at the end of each season — top 2 teams promote, bottom 2 relegate. Each league has 10 teams playing home-and-away (18 matches per format per season).',
      },
      {
        heading: 'Match Scheduling',
        text: 'Matches are scheduled automatically and cannot be postponed. Deadlines for lineup submission are 1 hour before match start. If no lineup is submitted, the AI will select your best available XI based on current form and fitness.',
      },
      {
        heading: 'Points System',
        text: 'T20 and One Day matches award 2 points for a win, 1 for a tie/no-result, and 0 for a loss. First Class matches award 12 points for a win, 4 for a first-innings lead in a draw, and 0 for a loss. Bonus points are awarded for scoring rates and wicket-taking rates.',
      },
      {
        heading: 'Tiebreakers',
        text: 'If teams are level on points at the end of a season, tiebreakers are applied in order: (1) Net Run Rate, (2) Head-to-head record, (3) Most wins, (4) Fewest losses. For First Class, batting and bowling bonus points are used before NRR.',
      },
    ],
  },
  {
    id: 'transfer-rules',
    icon: <HiOutlineArrowsRightLeft />,
    title: 'Transfer Market Rules',
    content: [
      {
        heading: 'Transfer Windows',
        text: 'The transfer market operates in two windows per season: the Pre-Season Window (open for 14 game-days before the season starts) and the Mid-Season Window (open for 7 game-days at the halfway point). Outside these windows, only free-agent signings are permitted.',
      },
      {
        heading: 'Listing & Bidding',
        text: 'Players can be listed for sale with a minimum asking price. Other managers can bid at or above this price. Auctions last 24 real hours. The highest bidder wins. If no bid meets the reserve, the player remains at the club. Each team may list a maximum of 3 players simultaneously.',
      },
      {
        heading: 'Transfer Fees & Sell-On Clauses',
        text: 'Transfer fees are paid in full at the time of transfer. A 10% sell-on clause is automatically attached to all transfers — if the buying club later sells the player, 10% of that future fee goes to the original selling club.',
      },
      {
        heading: 'Free Agents',
        text: 'Free agents (players without a club) can be signed at any time outside transfer windows. Signing a free agent costs only wages, no transfer fee. Free agent signings are limited to 2 per month to prevent roster churning.',
      },
    ],
  },
  {
    id: 'financial-rules',
    icon: <HiOutlineBanknotes />,
    title: 'Financial Fair Play',
    content: [
      {
        heading: 'Wage Budget',
        text: 'Your total wage bill must not exceed 70% of your club\'s revenue. If you exceed this limit, a wage cap will be enforced — preventing new signings until you reduce the wage bill. Persistent overspending triggers fines.',
      },
      {
        heading: 'Revenue Sources',
        text: 'Revenue comes from: match-day income (affected by ground capacity and fan count), league prize money, transfer profits, sponsorship deals (based on league tier and fan base), and merchandise. Upgrading your ground and growing your fan base directly grows revenue.',
      },
      {
        heading: 'Debt & Insolvency',
        text: 'Teams may go into debt, but excessive debt (exceeding 200% of annual revenue) triggers an insolvency warning. If debt is not reduced within 2 seasons, a points deduction of 10 points per format is applied. Continued insolvency may result in forced player sales.',
      },
      {
        heading: 'Prize Money',
        text: 'Season-end prize money is distributed based on final league position. The league champion receives the largest share, with diminishing amounts down to the last-place team. Promotion and cup victories award significant bonuses.',
      },
    ],
  },
  {
    id: 'conduct',
    icon: <HiOutlineHandRaised />,
    title: 'Code of Conduct',
    content: [
      {
        heading: 'Manager Behavior',
        text: 'Managers are expected to maintain respectful behavior in all interactions — including forums, messages, and team communications. Harassment, hate speech, discriminatory remarks, and toxic behavior will not be tolerated.',
      },
      {
        heading: 'Communication Guidelines',
        text: 'Keep all in-game communications constructive. Trash talk within friendly limits is fine, but personal attacks, threats, or doxxing are grounds for immediate and permanent suspension.',
      },
      {
        heading: 'Reporting Violations',
        text: 'If you witness a rule violation or suspicious behavior, use the in-game report system. All reports are reviewed by the admin team. False reporting to harass other managers is itself a violation.',
      },
    ],
  },
  {
    id: 'penalties',
    icon: <HiOutlineExclamationTriangle />,
    title: 'Penalties & Sanctions',
    content: [
      {
        heading: 'Warning System',
        text: 'Minor infractions result in warnings. Each manager has a 3-warning threshold. Exceeding this triggers automatic sanctions — starting with a 1-match transfer ban, escalating to points deductions, and ultimately account suspension.',
      },
      {
        heading: 'Points Deductions',
        text: 'Severe rule violations (financial fair play breaches, match-fixing, multi-accounting) may result in immediate points deductions. The standard deduction is 10 points across all formats, but this can be increased for repeat offences.',
      },
      {
        heading: 'Bans & Suspensions',
        text: 'Temporary bans range from 7 to 90 game-days. During a ban, your team is managed by AI and you cannot make transfers or changes. Permanent bans are reserved for the most serious violations and are irreversible.',
      },
      {
        heading: 'Appeals',
        text: 'All sanctions may be appealed within 7 game-days of issuance through the Support system. Appeals are reviewed by the admin team and a response is provided within 3 game-days. Decisions on appeal are final.',
      },
    ],
  },
  {
    id: 'seasons',
    icon: <HiOutlineClock />,
    title: 'Season & Scheduling',
    content: [
      {
        heading: 'Season Cycle',
        text: 'Each season lasts approximately 30 real days. The cycle follows: Pre-Season (transfers, training camps) → Season (league matches) → Post-Season (awards, promotions/relegations, contract expiries). There is a 3-day break between seasons.',
      },
      {
        heading: 'Match Frequency',
        text: 'Matches are played every 2 real days across all formats, rotating between T20, One Day, and First Class. Your team will play roughly 54 competitive matches per season (18 per format). Rest periods between formats ensure player fitness is manageable.',
      },
      {
        heading: 'End-of-Season Awards',
        text: 'At the end of each season, awards are given for: Best Batsman, Best Bowler, Best All-Rounder, Best Young Player, and Manager of the Season. Awards boost morale and attract higher-quality academy prospects.',
      },
    ],
  },
  {
    id: 'disputes',
    icon: <HiOutlineScale />,
    title: 'Dispute Resolution',
    content: [
      {
        heading: 'Transfer Disputes',
        text: 'If a transfer is contested (e.g. bid timing issues, glitches), both parties may file a dispute. The admin team will review transaction logs and resolve fairly. Disputed transfers are held in escrow until resolved.',
      },
      {
        heading: 'Match Result Disputes',
        text: 'Match results are final once processed by the game engine. However, if a technical error (server issue, calculation bug) is identified, the admin team may nullify and replay the match. Disputes must be filed within 24 hours of the match.',
      },
      {
        heading: 'Admin Decisions',
        text: 'The admin team has final authority on all disputes. Decisions are made based on game logs, rules, and the spirit of fair play. Repeated frivolous disputes may consume your appeal allocation for the season.',
      },
    ],
  },
];

export default function Rules() {
  const [openSections, setOpenSections] = useState(new Set(['general']));

  const toggle = (id) => {
    setOpenSections((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const expandAll = () => setOpenSections(new Set(sections.map((s) => s.id)));
  const collapseAll = () => setOpenSections(new Set());

  const scrollTo = (id) => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    setOpenSections((prev) => new Set(prev).add(id));
  };

  return (
    <div className="rules-page">
      {/* Header */}
      <div className="rules-header">
        <div className="rules-header-left">
          <HiOutlineShieldCheck className="rules-header-icon" />
          <div>
            <h1>Rules &amp; Regulations</h1>
            <p>Understand the rules that govern CricketPlex — fair play, squad limits, transfers, and more.</p>
          </div>
        </div>
        <div className="rules-header-actions">
          <button className="rules-action-btn" onClick={expandAll}>Expand All</button>
          <button className="rules-action-btn" onClick={collapseAll}>Collapse All</button>
        </div>
      </div>

      {/* Table of Contents */}
      <div className="rules-toc">
        <h3>Quick Navigation</h3>
        <div className="rules-toc-grid">
          {sections.map((s) => (
            <button key={s.id} className="rules-toc-item" onClick={() => scrollTo(s.id)}>
              <span className="rules-toc-icon">{s.icon}</span>
              {s.title}
            </button>
          ))}
        </div>
      </div>

      {/* Sections */}
      <div className="rules-sections">
        {sections.map((s) => {
          const isOpen = openSections.has(s.id);
          return (
            <div key={s.id} id={s.id} className={`rules-section ${isOpen ? 'open' : ''}`}>
              <button className="rules-section-toggle" onClick={() => toggle(s.id)}>
                <span className="rules-section-title">
                  <span className="rules-section-icon">{s.icon}</span>
                  <h2>{s.title}</h2>
                </span>
                {isOpen ? <HiOutlineChevronUp /> : <HiOutlineChevronDown />}
              </button>
              {isOpen && (
                <div className="rules-section-body">
                  {s.content.map((block, i) => (
                    <div key={i} className="rules-block">
                      <h3>{block.heading}</h3>
                      <p>{block.text}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
