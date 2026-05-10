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
  HiOutlineSignalSlash,
  HiOutlinePlayCircle,
  HiOutlineChatBubbleLeftRight,
  HiOutlineCalendarDays,
  HiOutlineShieldCheck,
  HiOutlineClipboardDocumentList,
  HiOutlineRectangleGroup,
} from 'react-icons/hi2';
import './GameManuals.css';

const sections = [
  {
    id: 'getting-started',
    icon: <HiOutlineSparkles />,
    title: 'Getting Started',
    color: '#22d3ee',
    content: [
      {
        heading: 'Welcome to CricketPlex',
        text: 'CricketPlex is a full cricket management simulation. You control every aspect of a cricket club — recruiting players, setting lineups, managing finances, upgrading your ground, trading on the transfer market, developing youth through the academy, and competing in three distinct league formats.',
      },
      {
        heading: 'Account & Team Setup',
        text: 'After signing up you\'ll be prompted to name your team and choose a country. This creates your club in the system. The country determines your pool of academy prospects and influences your fan base demographics.',
      },
      {
        heading: 'Navigation',
        text: 'Everything is accessible from the sidebar. Each section is a distinct module of the game — Dashboard, Squad, Lineup, Matches, Finances, Transfer Market, Ground, Academy, Training, Statistics, and more. Use the sidebar\'s collapse button on smaller screens.',
      },
    ],
  },
  {
    id: 'dashboard',
    icon: <HiOutlineRectangleGroup />,
    title: 'Dashboard',
    color: '#34d399',
    content: [
      {
        heading: 'Your Command Center',
        text: 'The Dashboard gives you an at-a-glance view of your club: current balance, squad size, fan count, team morale, world ranking per format, trophies won, and your next scheduled fixtures. It\'s the first thing you see on every login.',
      },
      {
        heading: 'Activity Feed',
        text: 'Recent activities are logged and shown on the dashboard — transfers completed, match results, training sessions, ground upgrades, academy pulls, and more. This keeps you updated on everything that has happened in the club, especially for events triggered by automated systems.',
      },
      {
        heading: 'Quick Stats',
        text: 'Key performance indicators are summarised: win percentage, current league positions across all three formats, and your team\'s overall rating. Use this to identify where you need to improve.',
      },
    ],
  },
  {
    id: 'squad',
    icon: <HiOutlineUserGroup />,
    title: 'Squad Management',
    color: '#a78bfa',
    content: [
      {
        heading: 'Players & Attributes',
        text: 'Every player has six core attributes: Batting, Bowling, Fielding, Fitness, Experience, and Morale. Each attribute is rated 0–100. A player\'s overall quality is derived from these, weighted by their primary role.',
      },
      {
        heading: 'Player Roles & Specialisations',
        text: 'Players are categorised as Batsman, Bowler, All-Rounder, or Wicket-Keeper. Sub-roles include openers, middle-order, finishers, fast bowlers, medium pacers, spinners, and more. A well-balanced squad across all roles is essential — you cannot field a competitive XI with only batsmen.',
      },
      {
        heading: 'Player Profiles',
        text: 'Clicking a player opens their full profile: attributes, career statistics (batting/bowling/fielding split by format), form indicator, age, nationality, fitness status, wage, contract length, and transfer value. This is your main tool for evaluating individual players.',
      },
      {
        heading: 'Fitness & Availability',
        text: 'Players accumulate fatigue from matches and training. Injured or unfit players cannot be selected for the XI. The Fitness Recovery system runs automatically each game day — rest players to keep them available for crucial matches.',
      },
      {
        heading: 'Ageing & Decline',
        text: 'Players age in real time with the game calendar. Young players (17–22) develop their attributes with match exposure and training. Players peak in their late 20s and gradually decline from their mid-30s. Planning for succession is a long-term management challenge.',
      },
    ],
  },
  {
    id: 'lineup',
    icon: <HiOutlineClipboardDocumentList />,
    title: 'Lineup & Strategy',
    color: '#fb923c',
    content: [
      {
        heading: 'Setting Your Playing XI',
        text: 'Before each match you select your playing eleven from your fit squad. The lineup page shows available players with their attributes highlighted. You must select a wicket-keeper and maintain a sensible batting order.',
      },
      {
        heading: 'Left & Right Hand Combinations',
        text: 'The match engine heavily penalizes bowlers who have to constantly adjust their line and length. Constructing partnerships that rotate the strike between a Left-Handed and Right-Handed batter will disrupt the bowler\'s rhythm and increase your scoring rate.',
      },
      {
        heading: 'Captain & Vice-Captain',
        text: 'Assign a captain and vice-captain in your XI. The captain\'s leadership score provides a morale bonus during matches. A strong captain can swing close matches in your favour.',
      },
      {
        heading: 'Default Lineups',
        text: 'You can save a default lineup per format (T20, OD, FC). When a match is upcoming and no lineup has been set, the default is used automatically. This prevents unselected XIs from causing automatic forfeit.',
      },
      {
        heading: 'First Class Strategy',
        text: 'For First Class (multi-day) matches you have an additional FC Strategy tab. Set your batting approach and bowling strategy. Be aware that FC pitches physically deteriorate as the match progresses — batting last on a Day 4 dustbowl is incredibly difficult, making your 1st Innings declaration timing crucial!',
      },
      {
        heading: 'Bowling Order',
        text: 'Specify which players bowl and in what order. Save your highly experienced (15+ XP), elite Fast Bowlers (80+ Rating) for the Death Overs — they have a special "Death Over Specialist" ability that halves the batting team\'s late-game scoring boost! Also note that bowlers take an over to find their rhythm, so constantly rotating them every over may lead to loose deliveries.',
      },
      {
        heading: 'New vs Old Ball Physics',
        text: 'The physical state of the ball changes over time. A hard, new ball swings and bounces, granting a significant advantage to Fast Bowlers but making it hard for Spinners to grip. Conversely, an old, scuffed ball grips the pitch beautifully for Spinners, while Fast Bowlers will struggle unless conditions are hot enough for reverse swing.',
      },
    ],
  },
  {
    id: 'matches',
    icon: <HiOutlineTrophy />,
    title: 'Matches & Leagues',
    color: '#22d3ee',
    content: [
      {
        heading: 'Three Formats',
        text: 'CricketPlex has three match formats — T20 (20 overs, fast-paced), One Day (50 overs, strategic), and First Class (multi-day, up to two innings each). Each format has its own separate league ladder, standings, and division system.',
      },
      {
        heading: 'League Structure & Divisions',
        text: 'Each format runs a round-robin league inside divisions. Win enough to finish in the promotion zone and move up a division; finish in the relegation zone and drop down. The top division title is the championship of that format.',
      },
      {
        heading: 'Fixtures & Schedule',
        text: 'Fixtures are generated at the start of each season. Home matches generate matchday revenue based on your stadium capacity and fan attendance. Away matches still count for prize money and standings but produce no ticket revenue.',
      },
      {
        heading: 'Match Simulation',
        text: 'Matches are simulated ball by ball by the Match Engine. It factors in batting vs. bowling attribute matchups, fitness levels, pitch conditions, team morale, weather, and a statistical randomness layer. No two matches play out identically.',
      },
      {
        heading: 'Sim Sessions',
        text: 'Sim sessions allow you to batch-advance the game calendar — simulating multiple game days at once. This is useful when you want to fast-forward through periods with no urgent decisions. Sessions can be paused at any point.',
      },
      {
        heading: 'World Rankings',
        text: 'Performance across all league matches contributes to a per-format world ranking. Rankings use a weighted points system based on win rate, margin of victory, and opponent strength. Higher rankings improve sponsorship deals and attract better transfer market talent.',
      },
    ],
  },
  {
    id: 'live-match',
    icon: <HiOutlinePlayCircle />,
    title: 'Live Match & Match Center',
    color: '#f472b6',
    content: [
      {
        heading: 'Live Match View',
        text: 'While a match is being simulated, the Live Match page shows real-time score updates ball by ball. You\'ll see the current partnership, last ball result, required run rate (in chases), and a running innings summary.',
      },
      {
        heading: 'Match Center',
        text: 'The Match Center is the full analytical view of any completed or in-progress match. It has multiple tabs: Scorecard (batting/bowling cards per innings), Commentary (ball-by-ball narrative), Charts (Manhattan, Worm, Run Rate graphs), and Over-by-Over breakdown.',
      },
      {
        heading: 'Commentary',
        text: 'Every ball is logged with narrative commentary. Look out for game-changing moments like Dropped Catches (influenced by fielding ratings) and Free Hits (awarded after a T20/ODI no-ball) where batters swing fearlessly without risk of being caught or bowled!',
      },
      {
        heading: 'Charts',
        text: 'Three interactive charts are available per match. The Manhattan chart shows runs-per-over as bars — hover over a bar to see a tooltip with runs and wickets in that over. The Worm chart shows cumulative score progression. The Run Rate chart shows over-by-over run rate trends. Lines are deliberately thin for readability.',
      },
      {
        heading: 'Scorecards',
        text: 'Full batting scorecards (runs, balls, 4s, 6s, strike rate, how out, bowler) and bowling scorecards (overs, maidens, runs, wickets, economy, extras) are generated per innings. First Class matches can have up to four innings across two teams.',
      },
    ],
  },
  {
    id: 'statistics',
    icon: <HiOutlineChartBar />,
    title: 'Statistics',
    color: '#22d3ee',
    content: [
      {
        heading: 'Team & Player Stats',
        text: 'The Statistics page shows aggregated career stats per player — across T20, One Day, and First Class formats separately. Batting stats include runs, average, strike rate, 50s, 100s. Bowling stats include wickets, average, economy, 5-wicket hauls.',
      },
      {
        heading: 'Matches Played',
        text: 'Match counts are based on lineup participation — a player counts as having played a match only if they were named in the playing XI for that fixture, regardless of whether they scored or took wickets.',
      },
      {
        heading: 'League Stats',
        text: 'Within each league, per-format leaderboards show top run scorers, top wicket takers, and best fielders (catches, run-outs, stumpings). These update after every simulated match.',
      },
      {
        heading: 'Fielding Stats',
        text: 'Fielding statistics track catches, stumpings (for wicket-keepers), and run-outs. Fielding quality contributes to overall player rating and affects some match engine calculations.',
      },
    ],
  },
  {
    id: 'academy',
    icon: <HiOutlineAcademicCap />,
    title: 'Academy',
    color: '#34d399',
    content: [
      {
        heading: 'Youth Development',
        text: 'Your academy generates young players (aged 16–19) periodically based on academy level. These prospects have raw attributes and a hidden potential ceiling. The higher your academy level, the better the talent that emerges.',
      },
      {
        heading: 'Recruiting Prospects',
        text: 'When an academy cohort is ready, you review each prospect\'s visible attributes and decide whether to promote them into your senior squad. Promoted players arrive with development headroom — their attributes can grow significantly with match exposure and training.',
      },
      {
        heading: 'Academy Upgrades',
        text: 'Invest in the academy to increase its level (up to a maximum tier). Higher tiers produce prospects with better base attributes, higher potential, and more specialised roles. Upgrades cost money and take game time to complete.',
      },
      {
        heading: 'Long-Term Strategy',
        text: 'A strong academy pipeline is the cheapest way to build squad depth. Young players you develop in-house will have no transfer fee, lower initial wages, and strong loyalty. A well-run academy can produce generational talents.',
      },
    ],
  },
  {
    id: 'transfers',
    icon: <HiOutlineArrowsRightLeft />,
    title: 'Transfer Market',
    color: '#fb923c',
    content: [
      {
        heading: 'Browse Listings',
        text: 'The transfer market shows players listed by other clubs (both human-managed and AI/bot teams). Each listing shows attributes, age, asking price, and wage expectations. You can filter by role, format specialty, age, and price range.',
      },
      {
        heading: 'Placing Bids',
        text: 'Submit a bid on any listed player. If your bid meets the asking price, the transfer completes immediately. Below the asking price, the selling club may accept, counter-offer, or reject. Negotiations happen in-game.',
      },
      {
        heading: 'Listing Your Players',
        text: 'Set a player as transfer-listed from their profile or the Transfer Market page. Set an asking price — if no offer arrives within the listing period, it expires and you can relist. Listing a popular player may upset squad morale temporarily.',
      },
      {
        heading: 'Transfer Auction',
        text: 'Some premium players go to auction — multiple clubs bid simultaneously in a timed window. The highest bidder wins. Auctions create competitive situations where demand for a key position can drive prices well above market value.',
      },
      {
        heading: 'Strategy',
        text: 'Smart transfer activity is essential. Sell ageing or surplus players before their value drops. Buy young talent cheap and develop them. Avoid overpaying on wages — a big wage bill constrains your flexibility for seasons to come.',
      },
    ],
  },
  {
    id: 'finances',
    icon: <HiOutlineBanknotes />,
    title: 'Finances',
    color: '#a78bfa',
    content: [
      {
        heading: 'Revenue',
        text: 'Income comes from: matchday ticket sales (scaled by stadium capacity and fan attendance), league prize money (higher for upper divisions), sponsorship deals (scale with world ranking and fan count), and transfer sell-on fees.',
      },
      {
        heading: 'Expenses',
        text: 'Weekly outgoings include player wages (highest expense for large squads), coaching staff salaries, stadium maintenance, academy running costs, and training facility costs. Transfer fees are one-time charges.',
      },
      {
        heading: 'Weekly Finance Cycle',
        text: 'Finances are processed weekly by the automated finance system. Each cycle calculates net balance, deducts wages, adds revenue, and logs every transaction. You can view a full transaction history on the Finances page.',
      },
      {
        heading: 'Budget Planning',
        text: 'The Finances page shows current balance, weekly wage bill, projected income, and a running chart of balance over time. Avoid letting your balance go negative — financial difficulty constrains transfers and may trigger automatic player sales.',
      },
    ],
  },
  {
    id: 'ground',
    icon: <HiOutlineBuildingOffice2 />,
    title: 'Ground Management',
    color: '#22d3ee',
    content: [
      {
        heading: 'Your Stadium',
        text: 'Your home ground starts as a modest venue. Matchday revenue scales directly with how many seats you have filled — a bigger stadium with a large fan base is your primary income driver long-term.',
      },
      {
        heading: 'Seat Expansion',
        text: 'Expand stadium sections to increase capacity: main stand, grandstand, media box, corporate boxes, and family terrace. Each section has multiple tiers costing progressively more. Plan expansions alongside fan growth — empty seats cost money to maintain.',
      },
      {
        heading: 'Pitch Configuration',
        text: 'Set your home pitch type: green and grassy (favours seam bowling), dry and dusty (favours spin), or a balanced surface. The pitch setting affects all home match simulations. Change it seasonally to suit your bowling attack.',
      },
      {
        heading: 'Floodlights & Facilities',
        text: 'Ground upgrades also include floodlights (enables evening matches and increases attendance), practice nets (boosts training efficiency), and media infrastructure (unlocks higher sponsorship tiers).',
      },
    ],
  },
  {
    id: 'training',
    icon: <HiOutlineBolt />,
    title: 'Training',
    color: '#fbbf24',
    content: [
      {
        heading: 'Training Sessions',
        text: 'Assign players to training programmes between matches. Sessions focus on a specific attribute: Batting, Bowling, Fielding, or Fitness. Each session improves the targeted attribute by an amount influenced by the player\'s potential, coaching quality, and current fatigue.',
      },
      {
        heading: 'Player Development',
        text: 'Younger players gain more from training than veterans. Additionally, players with high Stamina are natural athletes and will learn skills significantly faster. Ensure players maintain high Fitness, as training while exhausted will yield almost zero skill growth.',
      },
      {
        heading: 'Coaching Staff',
        text: 'Better coaching staff produce better training outcomes. Hire specialist coaches — a dedicated batting coach, bowling coach, and fielding coach each improve their respective training effectiveness. Good coaching is a worthwhile long-term investment.',
      },
      {
        heading: 'Training Logs',
        text: 'Every training session is recorded in the Training Logs — showing which players trained, what attribute was targeted, and the improvement gained. Use this to track development progression across your squad.',
      },
    ],
  },
  {
    id: 'fitness',
    icon: <HiOutlineShieldCheck />,
    title: 'Fitness & Recovery',
    color: '#34d399',
    content: [
      {
        heading: 'Fitness System',
        text: 'Fitness is a separate attribute (0–100) tracked independently from performance attributes. Playing matches and intensive training drains fitness. A player below a certain fitness threshold is unavailable for selection.',
      },
      {
        heading: 'Automatic Recovery',
        text: 'The Fitness Recovery System runs automatically each game day. Players not selected for a match recover fitness passively. The recovery rate depends on your medical staff quality and any active recovery programmes assigned.',
      },
      {
        heading: 'Injuries',
        text: 'Very low fitness or unlucky events during simulation can trigger injuries with a recovery duration. Injured players are unavailable for selection until they recover. Squad depth becomes critical when key players are injured.',
      },
      {
        heading: 'Managing Workload',
        text: 'Avoid playing the same XI in every match without rotation. Resting a player for one match can keep them at peak fitness for the next three. Planning rotation — especially during dense fixture schedules — is a key management skill.',
      },
    ],
  },
  {
    id: 'challenges',
    icon: <HiOutlineSignalSlash />,
    title: 'Friendly Challenges',
    color: '#f472b6',
    content: [
      {
        heading: 'What Are Friendly Challenges?',
        text: 'Friendly Challenges let you arrange head-to-head matches directly against another human-managed club in CricketPlex. These matches do not count towards league standings or prize money, but they do generate activity, morale, and player experience.',
      },
      {
        heading: 'Sending a Challenge',
        text: 'Browse the Challenges page to find other registered clubs. Select a club, choose the format (T20, OD, or FC), and send the challenge. The opposing manager is notified and can accept or decline.',
      },
      {
        heading: 'Accepting & Playing',
        text: 'Accepted challenges are scheduled as fixtures. Both clubs set their lineups and the match is simulated in the same way as league matches. A full scorecard and commentary is available in Match Center afterwards.',
      },
      {
        heading: 'Benefits',
        text: 'Friendly matches give younger players match time without the pressure of league results. They also contribute to player form and experience growth. Use them to trial new squad configurations before important league fixtures.',
      },
    ],
  },
  {
    id: 'chat',
    icon: <HiOutlineChatBubbleLeftRight />,
    title: 'Chat',
    color: '#22d3ee',
    content: [
      {
        heading: 'Chat Widget',
        text: 'The floating Chat Widget (bottom-right corner) allows you to send messages to other managers in CricketPlex. It stays accessible from every page so you can communicate without leaving your current task.',
      },
      {
        heading: 'Conversations',
        text: 'Start a new conversation by searching for another manager\'s team name. Each conversation is private between two users. All messages are persisted and loaded when you reopen the chat.',
      },
      {
        heading: 'Use Cases',
        text: 'Chat is useful for negotiating transfers directly with another manager (agreeing on a price before submitting a formal bid), discussing friendly challenges, or general competition banter.',
      },
    ],
  },
  {
    id: 'season',
    icon: <HiOutlineCalendarDays />,
    title: 'Season System',
    color: '#fbbf24',
    content: [
      {
        heading: 'Seasons & Game Weeks',
        text: 'The game progresses through seasons, each divided into game weeks. A game week advances the clock, processes match simulations, triggers automated events (finance cycles, fitness recovery, training logs), and moves the fixture calendar forward.',
      },
      {
        heading: 'Promotions & Relegations',
        text: 'At the end of each season, league standings are finalised. Top-placed clubs are promoted to a higher division; bottom-placed clubs are relegated. Promoted clubs face stronger opponents — plan your squad depth accordingly.',
      },
      {
        heading: 'Trophies',
        text: 'Winning a division championship or reaching milestone performances earns your club a permanent trophy. Trophies are displayed on your team profile and contribute to your club\'s overall prestige and reputation.',
      },
      {
        heading: 'Season Reset',
        text: 'After the season ends, player ages advance, contracts expire (requiring renewal or release), and a new fixture schedule is generated. Some automated events like academy pulls and training resets occur at season boundaries.',
      },
      {
        heading: 'Sim Sessions',
        text: 'Use the Sim Sessions feature to advance multiple game weeks at once. This is useful when you\'re comfortable with your squad, lineup, and plans. Any urgent event (injury, budget alert) will pause the session and notify you.',
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
            <p>Complete reference for managing your cricket empire — from first login to championship glory.</p>
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
              style={{ '--toc-color': s.color }}
              onClick={() => {
                setOpenSections((prev) => new Set([...prev, s.id]));
                setTimeout(() => {
                  document.getElementById(`manual-${s.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }, 50);
              }}
            >
              <span className="manuals-toc-icon" style={{ color: s.color }}>{s.icon}</span>
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
              className={`manuals-section${isOpen ? ' open' : ''}`}
              style={{ '--sec-color': section.color }}
            >
              <button className="manuals-section-toggle" onClick={() => toggleSection(section.id)}>
                <div className="manuals-section-title">
                  <span className="manuals-section-icon" style={{ color: section.color }}>{section.icon}</span>
                  <h2>{section.title}</h2>
                </div>
                <span className="manuals-chevron">
                  {isOpen ? <HiOutlineChevronUp /> : <HiOutlineChevronDown />}
                </span>
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
