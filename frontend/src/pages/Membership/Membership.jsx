// src/pages/MembershipPage.jsx
import React from "react";
import "./Membership.css";

const Membership = () => {
  const features = [
    "Profile Pic",
    "Team Logo",
    "Home League Game Time Change",
    "Create Tournaments and Add Members",
    "Create Group in Chat",
    "Track Player Improvement from Player Page",
    "Quick Select Last Games Lineup",
    "Play 5 Friendlies per Day",
    "Advanced Stats",
    "Shortlist Player from Transfer Market",
    "Auto Bid on Players up to Certain Price",
    "Publish Blog Regarding CricketPlex for Others to Read",
    "Franchise a Different and Independent Team from Main",
  ];

  return (
    <div className="membership-container">
      <header className="membership-header">
        <h1>CricketPlex Membership</h1>
        <p className="subtitle">🚀 Upcoming Feature — Unlock Advanced Cricket Management Tools</p>
      </header>

      <section className="membership-intro">
        <p>
          The <strong>CricketPlex Membership</strong> will empower managers and players with exclusive tools
          to enhance gameplay, analytics, and community engagement. Below is a preview of what’s coming soon.
        </p>
      </section>

      <section className="membership-features">
        {features.map((feature, index) => (
          <div key={index} className="feature-card">
            <h3>{feature}</h3>
            <p>Coming soon — stay tuned for updates!</p>
          </div>
        ))}
      </section>

      <footer className="membership-footer">
        <p>© 2026 CricketPlex | All rights reserved.</p>
      </footer>
    </div>
  );
};

export default Membership;