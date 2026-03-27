import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider } from './context/AuthContext';
import { PublicRoute, TeamSetupGuard, HomeGuard } from './components/RouteGuards';
import Signup from './pages/Auth/Signup';
import Login from './pages/Auth/Login';
import TeamSetup from './pages/TeamSetup/TeamSetup';
import Home from './pages/Home/Home';
import './App.css';

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Toaster
          position="top-right"
          toastOptions={{
            style: {
              background: '#1e293b',
              color: '#f1f5f9',
              border: '1px solid #334155',
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
    </AuthProvider>
  );
}

export default App;
