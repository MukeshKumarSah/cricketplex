import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { signup } from '../../api/auth';
import toast from 'react-hot-toast';
import './Auth.css';

export default function Signup() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    name: '',
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    acceptedTerms: false,
  });
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setForm((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (form.password !== form.confirmPassword) {
      toast.error('Passwords do not match');
      return;
    }

    if (!form.acceptedTerms) {
      toast.error('You must accept the rules and regulations');
      return;
    }

    setLoading(true);
    try {
      const res = await signup(form);
      toast.success(res.data?.message || 'Account created. Please verify your email.');
      navigate(`/verify-email?email=${encodeURIComponent(form.email)}`);
    } catch (err) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.errors
          ? Object.values(err.response.data.errors || {}).join(', ')
          : 'Signup failed';
      toast.error(msg || 'Signup failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-header">
          <h1>CricketPlex</h1>
          <p>Create your account</p>
        </div>

        <form onSubmit={handleSubmit} className="auth-form">
          <div className="form-group">
            <label htmlFor="name">Full Name</label>
            <input
              id="name"
              name="name"
              type="text"
              placeholder="Enter your full name"
              value={form.name}
              onChange={handleChange}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              name="username"
              type="text"
              placeholder="Choose a username"
              value={form.username}
              onChange={handleChange}
              required
              minLength={3}
              maxLength={30}
            />
          </div>

          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              name="email"
              type="email"
              placeholder="Enter your email"
              value={form.email}
              onChange={handleChange}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              placeholder="Create a password (min 8 characters)"
              value={form.password}
              onChange={handleChange}
              required
              minLength={8}
            />
          </div>

          <div className="form-group">
            <label htmlFor="confirmPassword">Confirm Password</label>
            <input
              id="confirmPassword"
              name="confirmPassword"
              type="password"
              placeholder="Confirm your password"
              value={form.confirmPassword}
              onChange={handleChange}
              required
            />
          </div>

          <div className="form-group checkbox-group">
            <label className="checkbox-label">
              <input
                type="checkbox"
                name="acceptedTerms"
                checked={form.acceptedTerms}
                onChange={handleChange}
              />
              <span>
                I accept the <a href="#rules">Rules & Regulations</a> and{' '}
                <a href="#policy">Privacy Policy</a>
              </span>
            </label>
          </div>

          <button type="submit" className="auth-btn" disabled={loading}>
            {loading ? 'Creating Account...' : 'Sign Up'}
          </button>
        </form>

        <div className="auth-footer">
          <p>
            Already have an account? <Link to="/login">Log In</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
