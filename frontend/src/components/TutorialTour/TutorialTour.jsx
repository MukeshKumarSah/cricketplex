import { useEffect, useRef, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { driver } from 'driver.js';
import 'driver.js/dist/driver.css';
import './TutorialTour.css';

const TOUR_KEY = 'tutorialCompleted';

// ── Main game menus (mirrors the sidebar order) ──
const MENU_STEPS = [
  {
    path: '/squad',
    element: '[data-tour="squad"]',
    popover: {
      title: '👥 Squad',
      description:
        "Your full playing roster. Use the role tabs (All, Batsman, Keeper, All-Rounder, Bowler) and multi-level sort (1st/2nd/3rd) with asc/desc. Each player card shows ratings, age, fitness and confidence — open a player for full details.",
    },
  },
  {
    path: '/matches',
    element: '[data-tour="matches"]',
    popover: {
      title: '🏆 Matches',
      description:
        "All your fixtures. Switch between Upcoming and Past, and filter by Season and Format. Each row shows the format tag, both teams and the weather — tap it to preview, set your lineup, or open the scorecard and commentary.",
    },
  },
  {
    path: '/cup',
    element: '[data-tour="cup"]',
    popover: {
      title: '⭐ Cup',
      description:
        "The season knockout cup. Walk the bracket round by round, check the seeds, view your own cup stats, and browse overall cup stats. Use the season selector to look back.",
    },
  },
  {
    path: '/tournaments',
    element: '[data-tour="tournaments"]',
    popover: {
      title: '🚩 Tournaments',
      description:
        "Friendly tournaments you create or join. Start one, join by code, or accept and decline invites. Open a tournament to see its fixtures and standings.",
    },
  },
  {
    path: '/challenges',
    element: '[data-tour="challenges"]',
    popover: {
      title: '⚡ Challenges',
      description:
        "Send and receive friendly challenges. A sidebar badge flags pending invites. A great way to test your squad and gain experience between league games.",
    },
  },
  {
    path: '/academy',
    element: '[data-tour="academy"]',
    popover: {
      title: '🎓 Academy',
      description:
        "Your youth setup. Upgrade or downgrade the academy level, track focused spots and funds, pull new young players by role, assign focused training, and review pull and training history.",
    },
  },
  {
    path: '/finances',
    element: '[data-tour="finances"]',
    popover: {
      title: '💰 Finances',
      description:
        "Your club's money. See income and expenses across sponsorship, prize money, gate revenue, wages and academy costs, and filter the transaction list.",
    },
  },
  {
    path: '/transfer-market',
    element: '[data-tour="transfer-market"]',
    popover: {
      title: '🔄 Transfer Market',
      description:
        "Buy and sell players. Browse active listings or your own, and filter by name, country, role, bat hand, bowl type, age, rating and skills. Auctions run live with timers and real-time bids.",
    },
  },
  {
    path: '/ground',
    element: '[data-tour="ground"]',
    popover: {
      title: '🏟️ Ground',
      description:
        "Manage your stadium. Set the default pitch, choose the pitch for each upcoming home match, and tune seating across Standing, Economy, Standard and Premium tiers to maximise gate revenue.",
    },
  },
  {
    path: '/stats',
    element: '[data-tour="stats"]',
    popover: {
      title: '📊 Stats',
      description:
        "Deep stats and leaderboards. Switch between Batting, Bowling and Fielding, and use the controls to pick Format, Match Type (Official, Friendly, Cup) and Season — including All seasons.",
    },
  },
  {
    path: '/team-list',
    element: '[data-tour="team-list"]',
    popover: {
      title: '📋 Team List',
      description:
        "Browse every team in the game. Compare ratings, squads and profiles against the competition.",
    },
  },
  {
    path: '/forums',
    element: '[data-tour="forums"]',
    popover: {
      title: '💬 Forums',
      description:
        "The community hub. Read and post in categories and threads to talk tactics with other managers. Admins can create new categories.",
    },
  },
  {
    path: '/blogs',
    element: '[data-tour="blogs"]',
    popover: {
      title: '📝 Blogs',
      description:
        "Community blogs. Read posts and react with emojis. If you are a supporter or admin you can write and publish your own.",
    },
  },
  {
    path: '/commentary/manage',
    element: '[data-tour="commentary/manage"]',
    popover: {
      title: '🎙️ Commentary',
      description:
        "Contribute to the match experience. Submit commentary templates, track your submissions, and import them via Excel. Approved lines are used in the live ball-by-ball coverage.",
    },
  },
  {
    path: '/game-manuals',
    element: '[data-tour="game-manuals"]',
    popover: {
      title: '📖 Game Manuals',
      description:
        "The complete guide. Detailed explanations of finances, training, the match engine, morale, fielding and more. Read this whenever you need help.",
    },
  },
  {
    path: '/rules',
    element: '[data-tour="rules"]',
    popover: {
      title: '📜 Rules & Regulations',
      description:
        "The rulebook. Fair-play policy, squad and finance rules, transfer rules and the penalties for breaking them.",
    },
  },
  {
    path: '/calendar',
    element: '[data-tour="calendar"]',
    popover: {
      title: '📅 Season Calendar',
      description:
        "The season at a glance. See each season day and what happens on it — matches, training, rest and special events.",
    },
  },
  {
    path: '/changelogs',
    element: '[data-tour="changelogs"]',
    popover: {
      title: '🗒️ ChangeLogs',
      description:
        "Product updates. Every new feature, fix and tweak is logged here (coming soon).",
    },
  },
  {
    path: '/support',
    element: '[data-tour="support"]',
    popover: {
      title: '❤️ Support the Game',
      description:
        "Support CricketPlex. Compare supporter plans and perks, and contribute to help the game keep growing.",
    },
  },
  {
    path: '/search',
    element: '[data-tour="search"]',
    popover: {
      title: '🔍 Search',
      description:
        "Find anything fast. Search across managers, players, teams and leagues.",
    },
  },
];

// ── Admin-only menus ──
const ADMIN_STEPS = [
  {
    path: '/admin/players',
    element: '[data-tour="admin/players"]',
    popover: {
      title: '🛡️ Player Pool',
      description:
        "Admin tools for the name pool. Add or delete first and last names per country, view combination stats, assign squads to teams, and override supporter status without payment.",
    },
  },
  {
    path: '/admin/leagues',
    element: '[data-tour="admin/leagues"]',
    popover: {
      title: '🌐 Leagues',
      description:
        "Admin league management. Create leagues, generate bot teams, manage leagues per country, and run dev jobs like aging, fitness, training, seasonal rollover and fast-forward.",
    },
  },
  {
    path: '/admin/bots',
    element: '[data-tour="admin/bots"]',
    popover: {
      title: '🤖 Bot Teams',
      description:
        "Admin view of every bot-controlled team. Search and sort them to keep the game world balanced.",
    },
  },
  {
    path: '/admin/formulas',
    element: '[data-tour="admin/formulas"]',
    popover: {
      title: '🧮 Game Formulas',
      description:
        "Admin reference for every formula and mechanic in the match engine, finances, training and morale.",
    },
  },
  {
    path: '/admin/sim',
    element: '[data-tour="admin/sim"]',
    popover: {
      title: '🧪 Sim Lab',
      description:
        "Build custom simulation sessions — set lineups, pitches and conditions — to test the match engine before changes go live.",
    },
  },
  {
    path: '/admin/commentary',
    element: '[data-tour="admin/commentary"]',
    popover: {
      title: '🗂️ Commentary Review',
      description:
        "Admin moderation of user-submitted commentary. Approve, reject or edit submissions before they enter the live ball-by-ball feed.",
    },
  },
];

function buildSteps(isAdmin) {
  const intro = {
    path: '/',
    element: '.home-content',
    popover: {
      title: '👋 Welcome to CricketPlex!',
      description:
        "This is your cricket management HQ. Let's take a full tour of every menu so you know exactly where everything lives.",
    },
  };

  const dashboard = {
    path: '/',
    element: '[data-tour="dashboard"]',
    popover: {
      title: '🏠 Dashboard',
      description:
        "Your home base. At a glance: team header with stadium seats, fans and trophies; a 7-day weather forecast; home ground and recent attendance; upcoming fixtures; league standings with form; the team morale ring; and a fans card.",
    },
  };

  const timeStep = {
    path: '/',
    element: '.top-bar-center',
    popover: {
      title: '📅 Season & Time',
      description:
        "The game runs in real time (UTC). Matches happen on set days, training runs daily, and a season lasts several weeks. Keep an eye on the clock.",
    },
  };

  const steps = [intro, dashboard, ...MENU_STEPS];
  if (isAdmin) steps.push(...ADMIN_STEPS);
  steps.push(timeStep);

  const last = steps[steps.length - 1];
  steps.push({
    path: last.path,
    element: '.home-content',
    popover: {
      title: "🎉 You're all set!",
      description:
        "Explore the menus at your own pace. You can replay this tour any time from Settings → Tutorial. Good luck, coach!",
    },
  });

  return steps;
}

export default function TutorialTour({ sidebarOpen, setSidebarOpen }) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const driverRef = useRef(null);
  const hasStarted = useRef(false);

  const isAdmin = user?.role === 'ADMIN';
  const tourSteps = useMemo(() => buildSteps(isAdmin), [isAdmin]);

  const startTour = useCallback(() => {
    if (driverRef.current) {
      driverRef.current.destroy();
    }

    // Make sure sidebar is open so tour targets are visible
    if (!sidebarOpen) setSidebarOpen(true);

    const goToStep = (fromIdx, toIdx, driverObj, dir) => {
      const from = tourSteps[fromIdx];
      const to = tourSteps[toIdx];

      if (to.path !== from.path) {
        navigate(to.path);
        setTimeout(() => {
          dir === 'next' ? driverObj.moveNext() : driverObj.movePrevious();
        }, 450);
      } else {
        dir === 'next' ? driverObj.moveNext() : driverObj.movePrevious();
      }
    };

    const driverObj = driver({
      showProgress: true,
      animate: true,
      smoothScroll: true,
      allowClose: true,
      overlayColor: 'rgba(0, 0, 0, 0.65)',
      stagePadding: 8,
      stageRadius: 12,
      popoverClass: 'tour-popover',
      nextBtnText: 'Next →',
      prevBtnText: '← Back',
      doneBtnText: 'Finish Tour ✓',
      progressText: '{{current}} of {{total}}',
      onDestroyStarted: () => {
        localStorage.setItem(TOUR_KEY, 'true');
        driverObj.destroy();
        driverRef.current = null;
      },
      steps: tourSteps.map((step, i) => ({
        element: step.element,
        popover: {
          ...step.popover,
          side: step.element?.includes('data-tour') ? 'right' : 'bottom',
          align: 'start',

          onNextClick: () => {
            const nextIdx = i + 1;
            if (nextIdx >= tourSteps.length) {
              localStorage.setItem(TOUR_KEY, 'true');
              driverObj.destroy();
              driverRef.current = null;
              navigate('/');
              return;
            }
            goToStep(i, nextIdx, driverObj, 'next');
          },

          onPrevClick: () => {
            const prevIdx = i - 1;
            if (prevIdx < 0) return;
            goToStep(i, prevIdx, driverObj, 'prev');
          },
        },
      })),
    });

    driverRef.current = driverObj;
    // Small delay to ensure DOM is ready
    setTimeout(() => driverObj.drive(), 300);
  }, [navigate, sidebarOpen, setSidebarOpen, tourSteps]);

  // Auto-start for first-time users
  useEffect(() => {
    if (hasStarted.current) return;
    const completed = localStorage.getItem(TOUR_KEY);
    if (!completed) {
      hasStarted.current = true;
      const timer = setTimeout(() => startTour(), 800);
      return () => clearTimeout(timer);
    }
  }, [startTour]);

  // Expose restart globally so Settings page can trigger it
  useEffect(() => {
    window.__restartTour = () => {
      localStorage.removeItem(TOUR_KEY);
      navigate('/');
      setTimeout(() => startTour(), 500);
    };
    return () => { delete window.__restartTour; };
  }, [startTour, navigate]);

  return null; // Render nothing — driver.js operates on the DOM directly
}
