import { useState } from 'react';
import {
  HiOutlineBookOpen,
  HiOutlineChevronDown,
  HiOutlineChevronUp,
  HiOutlineUserGroup,
  HiOutlineTrophy,
  HiOutlineBanknotes,
  HiOutlineArrowsRightLeft,
  HiOutlineBuildingOffice2,
  HiOutlineAcademicCap,
  HiOutlineChartBar,
  HiOutlineBolt,
  HiOutlineSparkles,
} from 'react-icons/hi2';
import './GameManuals.css';

const sections = [
  {
    id: 'getting-started',
    icon: <HiOutlineSparkles />,
    title: 'Getting Started',
    content: [
      {
        heading: 'Welcome to CricketPlex',
        text: 'CricketPlex is the ultimate cricket management simulation. You take charge of a cricket team — hiring players, managing finances, upgrading your ground, competing in leagues across three formats (T20, One Day, and First Class), and building a dynasty.',
      },
      {
        heading: 'First Steps',
        text: 'After signing up and setting up your team name and country, you\'ll land on the Dashboard. This is your command center — showing your team overview, league positions, morale, fans, trophies, and recent activities. From here, use the sidebar to navigate to different sections of the game.',
      },
      {
        heading: 'Game Progression',
        text: 'The game runs in seasons. Each season consists of league matches across all three formats. Between matches, you can train players, scout academy talent, trade on the transfer market, and upgrade your ground. Your decisions shape the team\'s future.',
      },
    ],
  },
  {
    id: 'squad',
    icon: <HiOutlineUserGroup />,
    title: 'Squad Management',
    content: [
      {
        heading: 'Your Squad',
        text: 'Your squad is the heart of your team. Each player has attributes like batting, bowling, fielding, fitness, and experience. Players age over time, peak in their mid-20s to early 30s, and eventually decline. Managing your squad composition for both present and future is key.',
      },
      {
        heading: 'Player Roles',
        text: 'Players have primary roles: Batsman, Bowler, All-Rounder, and Wicket-Keeper. Each role has sub-specializations — opening batsman, middle-order, fast bowler, spinner, etc. Building a balanced squad across all roles is essential for consistent performance.',
      },
      {
        heading: 'Setting Lineups',
        text: 'Before each match, you must set your playing XI. Choose wisely based on the format (T20/OD/FC), opponent strengths, pitch conditions, and player form. The lineup can include a captain and vice-captain whose leadership affects team morale during matches.',
      },
    ],
  },
  {
    id: 'matches',
    icon: <HiOutlineTrophy />,
    title: 'Matches & Leagues',
    content: [
      {
        heading: 'Three Formats',
        text: 'CricketPlex features three match formats: T20 (fast-paced, high action), One Day (50-over strategic battles), and First Class (multi-day test of endurance and skill). Each format has its own league with divisions, promotions, and relegations.',
      },
      {
        heading: 'League Structure',
        text: 'Each format has multiple divisions. Win enough matches to earn promotion to a higher division. Perform poorly and you risk relegation. Your goal is to climb to Division 1 and win the championship in all three formats.',
      },
      {
        heading: 'World Rankings',
        text: 'Your performance across leagues contributes to a World Ranking for each format. Rankings are calculated based on wins, losses, draws, and the strength of opponents. A top world ranking brings prestige, more fans, and better sponsorship deals.',
      },
      {
        heading: 'Match Simulation',
        text: 'Matches are simulated based on player attributes, team morale, lineup choices, pitch conditions, and a touch of randomness. You\'ll see a detailed scorecard after each match with individual performances.',
      },
    ],
  },
  {
    id: 'academy',
    icon: <HiOutlineAcademicCap />,
    title: 'Academy',
    content: [
      {
        heading: 'Youth Development',
        text: 'The Academy is where future stars are born. It generates young players periodically based on your academy level. Higher-level academies produce better talent with higher potential.',
      },
      {
        heading: 'Recruiting from Academy',
        text: 'When an academy player is ready, you can recruit them into your senior squad. They\'ll start with lower attributes but have growth potential that, with proper training and match exposure, can make them world-class.',
      },
      {
        heading: 'Academy Upgrades',
        text: 'Invest in your academy to increase its level. Better academies have better coaching staff, facilities, and scouting networks — resulting in higher-quality youth prospects.',
      },
    ],
  },
  {
    id: 'finances',
    icon: <HiOutlineBanknotes />,
    title: 'Finances',
    content: [
      {
        heading: 'Revenue Streams',
        text: 'Your income comes from multiple sources: matchday revenue (ticket sales based on ground size and fan count), sponsorships (improve with world ranking), prize money from leagues, and player sales on the transfer market.',
      },
      {
        heading: 'Expenses',
        text: 'Running a team costs money. You\'ll pay player wages (based on their quality), ground maintenance, academy upkeep, training costs, and transfer fees when buying players. Keep your budget balanced to avoid financial trouble.',
      },
      {
        heading: 'Budget Management',
        text: 'The Finances page gives you a complete overview of income vs expenses, current balance, and projections. Plan ahead — overcommitting on wages or a big transfer can cripple your finances for seasons to come.',
      },
    ],
  },
  {
    id: 'transfers',
    icon: <HiOutlineArrowsRightLeft />,
    title: 'Transfer Market',
    content: [
      {
        heading: 'Buying Players',
        text: 'Browse the transfer market to find players listed by other teams or AI-managed clubs. Each listing shows the player\'s attributes, age, asking price, and contract details. Make an offer and negotiate.',
      },
      {
        heading: 'Selling Players',
        text: 'List your own players on the transfer market with an asking price. Other teams may make offers. If the player doesn\'t sell within a set period, the listing expires and you can relist or keep the player.',
      },
      {
        heading: 'Transfer Strategy',
        text: 'Smart transfers can transform your team. Buy undervalued young talent, develop them, and sell at a profit. Or invest in experienced stars to push for immediate success. Balance short-term needs with long-term planning.',
      },
    ],
  },
  {
    id: 'ground',
    icon: <HiOutlineBuildingOffice2 />,
    title: 'Ground Management',
    content: [
      {
        heading: 'Your Stadium',
        text: 'Your ground is where home matches are played. It starts small but can be upgraded with more seats, better facilities, floodlights, and pitch improvements. A bigger, better ground generates more matchday revenue.',
      },
      {
        heading: 'Upgrades',
        text: 'Ground upgrades include: expanding seating capacity, adding corporate boxes, installing floodlights (required for night matches), improving the pitch quality, and adding practice nets. Each upgrade costs money and takes time to complete.',
      },
      {
        heading: 'Pitch Conditions',
        text: 'Your home pitch can be configured — green and seaming for fast bowlers, dry and dusty for spinners, or a balanced batting-friendly surface. Use this to your advantage based on your team\'s strengths.',
      },
    ],
  },
  {
    id: 'training',
    icon: <HiOutlineBolt />,
    title: 'Training',
    content: [
      {
        heading: 'Training Sessions',
        text: 'Schedule training sessions between matches to improve player attributes. Focus on batting, bowling, fielding, or fitness. Training effectiveness depends on player potential, coaching quality, and facilities.',
      },
      {
        heading: 'Player Development',
        text: 'Young players respond best to training, improving faster than veterans. However, over-training can lead to fatigue and injuries. Balance training intensity with rest periods.',
      },
      {
        heading: 'Coaching Staff',
        text: 'Better coaches produce better training results. Invest in coaching staff to improve training efficiency. Specialist coaches (batting coach, bowling coach, fielding coach) boost specific areas.',
      },
    ],
  },
  {
    id: 'morale-fans',
    icon: <HiOutlineChartBar />,
    title: 'Morale & Fans',
    content: [
      {
        heading: 'Team Morale',
        text: 'Morale affects match performance. Winning matches, fair wages, good facilities, and strong squad depth boost morale. Losing streaks, financial troubles, and selling popular players lower it. Keep morale high for peak performance.',
      },
      {
        heading: 'Fan Base',
        text: 'Fans grow with success. Winning matches, climbing divisions, and having star players attracts more fans. More fans mean higher matchday revenue and better sponsorship deals. Fan count can also decrease with prolonged poor performance.',
      },
      {
        heading: 'Reputation',
        text: 'Your team\'s reputation is built over seasons of performance. A strong reputation makes it easier to attract quality players in transfers and produces better academy talent.',
      },
    ],
  },
];

export default function GameManuals() {
  const [openSections, setOpenSections] = useState(new Set(['getting-started']));

  const toggleSection = (id) => {
    setOpenSections((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const expandAll = () => setOpenSections(new Set(sections.map((s) => s.id)));
  const collapseAll = () => setOpenSections(new Set());

  return (
    <div className="manuals-page">
      <div className="manuals-header">
        <div className="manuals-header-left">
          <HiOutlineBookOpen className="manuals-header-icon" />
          <div>
            <h1>Game Manuals</h1>
            <p>Everything you need to know about managing your cricket empire.</p>
          </div>
        </div>
        <div className="manuals-header-actions">
          <button className="manuals-action-btn" onClick={expandAll}>Expand All</button>
          <button className="manuals-action-btn" onClick={collapseAll}>Collapse All</button>
        </div>
      </div>

      <div className="manuals-toc">
        <h3>Quick Navigation</h3>
        <div className="manuals-toc-grid">
          {sections.map((s) => (
            <button
              key={s.id}
              className="manuals-toc-item"
              onClick={() => {
                setOpenSections((prev) => new Set([...prev, s.id]));
                document.getElementById(`manual-${s.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
              }}
            >
              <span className="manuals-toc-icon">{s.icon}</span>
              <span>{s.title}</span>
            </button>
          ))}
        </div>
      </div>

      <div className="manuals-sections">
        {sections.map((section) => {
          const isOpen = openSections.has(section.id);
          return (
            <section
              key={section.id}
              id={`manual-${section.id}`}
              className={`manuals-section ${isOpen ? 'open' : ''}`}
            >
              <button className="manuals-section-toggle" onClick={() => toggleSection(section.id)}>
                <div className="manuals-section-title">
                  <span className="manuals-section-icon">{section.icon}</span>
                  <h2>{section.title}</h2>
                </div>
                {isOpen ? <HiOutlineChevronUp /> : <HiOutlineChevronDown />}
              </button>
              {isOpen && (
                <div className="manuals-section-body">
                  {section.content.map((item, i) => (
                    <div className="manuals-block" key={i}>
                      <h3>{item.heading}</h3>
                      <p>{item.text}</p>
                    </div>
                  ))}
                </div>
              )}
            </section>
          );
        })}
      </div>
    </div>
  );
}
