import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import { NotificationProvider } from './context/NotificationContext';
import { PublicRoute, TeamSetupGuard, HomeGuard } from './components/RouteGuards';
import Signup from './pages/Auth/Signup';
import Login from './pages/Auth/Login';
import VerifyEmail from './pages/Auth/VerifyEmail';
import ForgotPassword from './pages/Auth/ForgotPassword';
import ResetPassword from './pages/Auth/ResetPassword';
import TeamSetup from './pages/TeamSetup/TeamSetup';
import TeamSetupSecondary from './pages/TeamSetupSecondary/TeamSetupSecondary';
import Home from './pages/Home/Home';
import './App.css';

function App() {
  return (
    <AuthProvider>
      <ThemeProvider>
        <NotificationProvider>
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
            <Route path="/forgot-password" element={<ForgotPassword />} />
            <Route path="/reset-password" element={<ResetPassword />} />
            <Route path="/verify-email" element={<VerifyEmail />} />
            <Route
              path="/team-setup"
              element={
                <TeamSetupGuard>
                  <TeamSetup />
                </TeamSetupGuard>
              }
            />
            <Route
              path="/team-setup-secondary"
              element={
                <TeamSetupGuard>
                  <TeamSetupSecondary />
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
        </NotificationProvider>
      </ThemeProvider>
    </AuthProvider>
  );
}

export default App;
