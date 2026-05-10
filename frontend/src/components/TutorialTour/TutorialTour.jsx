import { useEffect, useRef, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { driver } from 'driver.js';
import 'driver.js/dist/driver.css';
import './TutorialTour.css';

const TOUR_KEY = 'tutorialCompleted';

const tourSteps = [
  {
    path: '/',
    element: '.home-content',
    popover: {
      title: '👋 Welcome to CricketPlex!',
      description:
        'This is your cricket management HQ. Let\'s take a quick tour of the key areas so you can hit the ground running.',
    },
  },
  {
    path: '/',
    element: '[data-tour="dashboard"]',
    popover: {
      title: '🏠 Dashboard',
      description:
        'Your home base. See upcoming matches, recent results, league standings, weather forecast, and key stats at a glance.',
    },
  },
  {
    path: '/squad',
    element: '[data-tour="squad"]',
    popover: {
      title: '👥 Squad',
      description:
        'View and manage your players. Check ratings, fitness, confidence, and set training focus for each player.',
    },
  },
  {
    path: '/matches',
    element: '[data-tour="matches"]',
    popover: {
      title: '🏆 Matches',
      description:
        'See all your upcoming fixtures across T20, ODI, and First-Class formats. Set your lineup before each match!',
    },
  },
  {
    path: '/challenges',
    element: '[data-tour="challenges"]',
    popover: {
      title: '⚡ Challenges',
      description:
        'Challenge other teams to friendly matches. A great way to test your squad and gain experience outside the league.',
    },
  },
  {
    path: '/academy',
    element: '[data-tour="academy"]',
    popover: {
      title: '🎓 Academy',
      description:
        'Your youth development center. Upgrade your academy level to unlock focused training slots and pull young talent into your squad.',
    },
  },
  {
    path: '/finances',
    element: '[data-tour="finances"]',
    popover: {
      title: '💰 Finances',
      description:
        'Track your income and expenses. Sponsorship, prize money, gate revenue, wages, and academy costs — all in one place.',
    },
  },
  {
    path: '/transfer-market',
    element: '[data-tour="transfer-market"]',
    popover: {
      title: '🔄 Transfer Market',
      description:
        'Buy and sell players. List players for sale or bid on others. Build the perfect squad through smart transfers.',
    },
  },
  {
    path: '/ground',
    element: '[data-tour="ground"]',
    popover: {
      title: '🏟️ Ground',
      description:
        'Manage your stadium. Adjust seating capacity across Standing, Economy, Standard, and Premium tiers to maximise gate revenue.',
    },
  },
  {
    path: '/stats',
    element: '[data-tour="stats"]',
    popover: {
      title: '📊 Stats',
      description:
        'Deep stats and leaderboards. Compare players, track performance trends, and find the best performers.',
    },
  },
  {
    path: '/team-list',
    element: '[data-tour="team-list"]',
    popover: {
      title: '📋 Team List',
      description:
        'Browse all teams in the game. See their squads, ratings, and compare yourself against the competition.',
    },
  },
  {
    path: '/game-manuals',
    element: '[data-tour="game-manuals"]',
    popover: {
      title: '📖 Game Manuals',
      description:
        'The complete guide to game mechanics — finances, training, match engine, morale, and more. Read this if you need help!',
    },
  },
  {
    path: '/game-manuals',
    element: '.top-bar-center',
    popover: {
      title: '📅 Season & Time',
      description:
        'The game runs in real-time UTC. Matches happen on set days, training runs daily, and seasons last 8 weeks. Keep an eye on the clock!',
    },
  },
  {
    path: '/game-manuals',
    element: '.home-content',
    popover: {
      title: '🎉 You\'re all set!',
      description:
        'Head back to the Dashboard and start managing your club. Good luck, coach! You can restart this tour anytime from Settings.',
    },
  },
];

export default function TutorialTour({ sidebarOpen, setSidebarOpen }) {
  const navigate = useNavigate();
  const location = useLocation();
  const driverRef = useRef(null);
  const hasStarted = useRef(false);

  const startTour = useCallback(() => {
    if (driverRef.current) {
      driverRef.current.destroy();
    }

    // Make sure sidebar is open so tour targets are visible
    if (!sidebarOpen) setSidebarOpen(true);

    let currentStepIndex = 0;

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

            const nextStep = tourSteps[nextIdx];
            currentStepIndex = nextIdx;

            if (nextStep.path !== location.pathname && nextStep.path !== tourSteps[i].path) {
              navigate(nextStep.path);
              // Wait for page transition then move
              setTimeout(() => {
                driverObj.moveNext();
              }, 400);
            } else {
              driverObj.moveNext();
            }
          },

          onPrevClick: () => {
            const prevIdx = i - 1;
            if (prevIdx < 0) return;

            const prevStep = tourSteps[prevIdx];
            currentStepIndex = prevIdx;

            if (prevStep.path !== tourSteps[i].path) {
              navigate(prevStep.path);
              setTimeout(() => {
                driverObj.movePrevious();
              }, 400);
            } else {
              driverObj.movePrevious();
            }
          },
        },
      })),
    });

    driverRef.current = driverObj;
    // Small delay to ensure DOM is ready
    setTimeout(() => driverObj.drive(), 300);
  }, [navigate, location.pathname, sidebarOpen, setSidebarOpen]);

  // Auto-start for first-time users
  useEffect(() => {
    if (hasStarted.current) return;
    const completed = localStorage.getItem(TOUR_KEY);
    if (!completed) {
      hasStarted.current = true;
      // Wait for initial page load
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
