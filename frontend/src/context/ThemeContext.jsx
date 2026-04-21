import { createContext, useContext, useEffect, useState } from 'react';
import { useAuth } from './AuthContext';
import { updateTheme as updateThemeApi } from '../api/auth';

const ThemeContext = createContext(null);

export function ThemeProvider({ children }) {
  const { user, updateUser } = useAuth();

  // Initialise from localStorage immediately so there is no flash
  const [theme, setTheme] = useState(
    () => localStorage.getItem('theme') || 'dark'
  );

  // Apply data-theme on every theme change
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  // Sync from the authenticated user object once it loads
  useEffect(() => {
    if (user?.theme && user.theme !== theme) {
      setTheme(user.theme);
      localStorage.setItem('theme', user.theme);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.theme]);

  const switchTheme = async (newTheme) => {
    const prev = theme;
    setTheme(newTheme);
    localStorage.setItem('theme', newTheme);

    if (user) {
      try {
        await updateThemeApi(newTheme);
        updateUser({ ...user, theme: newTheme });
      } catch {
        // Revert on failure
        setTheme(prev);
        localStorage.setItem('theme', prev);
      }
    }
  };

  return (
    <ThemeContext.Provider value={{ theme, switchTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export const useTheme = () => useContext(ThemeContext);
