import './ComingSoon.css';

export default function ComingSoon({ icon, title, description }) {
  return (
    <div className="cs-page">
      <div className="cs-card">
        <div className="cs-icon">{icon}</div>
        <h1 className="cs-title">{title}</h1>
        <p className="cs-desc">{description}</p>
        <div className="cs-badge">Coming Soon</div>
      </div>
    </div>
  );
}
