import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { resendVerification, verifyEmail } from '../../api/auth';
import toast from 'react-hot-toast';
import './Auth.css';

export default function VerifyEmail() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const emailFromQuery = searchParams.get('email') || '';
  const [email, setEmail] = useState(emailFromQuery);
  const [verifying, setVerifying] = useState(Boolean(token));
  const [verified, setVerified] = useState(false);
  const [resendLoading, setResendLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState(
    token ? 'Verifying your email...' : 'Check your inbox and click the verification link.'
  );

  useEffect(() => {
    if (!token) return;
    let mounted = true;
    verifyEmail(token)
      .then((res) => {
        if (!mounted) return;
        setVerified(true);
        setStatusMessage(res.data?.message || 'Email verified successfully.');
      })
      .catch((err) => {
        if (!mounted) return;
        setStatusMessage(err.response?.data?.message || 'Verification failed. Request a new link.');
      })
      .finally(() => {
        if (mounted) setVerifying(false);
      });
    return () => {
      mounted = false;
    };
  }, [token]);

  const handleResend = async (e) => {
    e.preventDefault();
    if (!email.trim()) {
      toast.error('Please enter your email');
      return;
    }
    setResendLoading(true);
    try {
      const res = await resendVerification(email.trim());
      toast.success(res.data?.message || 'Verification email sent.');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Could not send verification email');
    } finally {
      setResendLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-header">
          <h1>CricketPlex</h1>
          <p>Email Verification</p>
        </div>

        <div className="auth-form">
          <p style={{ marginBottom: 16 }}>{statusMessage}</p>

          {!verified && !verifying && (
            <form onSubmit={handleResend}>
              <div className="form-group">
                <label htmlFor="email">Resend verification to</label>
                <input
                  id="email"
                  name="email"
                  type="email"
                  placeholder="Enter your account email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>
              <button type="submit" className="auth-btn" disabled={resendLoading}>
                {resendLoading ? 'Sending...' : 'Resend Verification Email'}
              </button>
            </form>
          )}

          <div className="auth-footer" style={{ marginTop: 16 }}>
            <p>
              <Link to="/login">Back to Login</Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
