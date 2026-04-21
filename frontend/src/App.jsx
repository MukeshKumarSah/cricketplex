import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import { PublicRoute, TeamSetupGuard, HomeGuard } from './components/RouteGuards';
import Signup from './pages/Auth/Signup';
import Login from './pages/Auth/Login';
import TeamSetup from './pages/TeamSetup/TeamSetup';
import Home from './pages/Home/Home';
import './App.css';

function App() {
  return (
    <AuthProvider>
      <ThemeProvider>
        <BrowserRouter>
          <Toaster
            position="top-right"
            toastOptions={{
              style: {
                background: 'var(--dropdown-bg)',
                color: 'var(--text-1)',
                border: '1px solid var(--border)',
              },
            }}
          />
          <Routes>
            <Route
              path="/signup"
              element={
                <PublicRoute>
                  <Signup />
                </PublicRoute>
              }
            />
            <Route
              path="/login"
              element={
                <PublicRoute>
                  <Login />
                </PublicRoute>
              }
            />
            <Route
              path="/team-setup"
              element={
                <TeamSetupGuard>
                  <TeamSetup />
                </TeamSetupGuard>
              }
            />
            <Route
              path="/*"
              element={
                <HomeGuard>
                  <Home />
                </HomeGuard>
              }
            />
          </Routes>
        </BrowserRouter>
      </ThemeProvider>
    </AuthProvider>
  );
}

export default App;
