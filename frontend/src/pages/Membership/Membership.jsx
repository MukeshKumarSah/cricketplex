import React from "react";
import {
  HiOutlineSparkles,
  HiOutlineUser,
  HiOutlineCamera,
  HiOutlineClock,
  HiOutlineTrophy,
  HiOutlineChatBubbleLeftRight,
  HiOutlineChartBar,
  HiOutlineClipboardDocumentList,
  HiOutlinePlayCircle,
  HiOutlineScale,
  HiOutlineHeart,
  HiOutlineCurrencyDollar,
  HiOutlineBookOpen,
  HiOutlineBuildingOffice2,
  HiOutlineGlobeAlt,
  HiOutlineMagnifyingGlass,
  HiOutlineChartPie,
  HiOutlineCpuChip,
  HiOutlineMapPin,
  HiOutlineShieldCheck,
} from "react-icons/hi2";
import "./Membership.css";

const Membership = () => {
  const features = [
    { title: "Global Franchises", desc: "Expand your empire. Manage an independent secondary B-team in a completely different region.", icon: <HiOutlineGlobeAlt /> },
    { title: "Academy Scouting Network", desc: "Remove the guesswork. Reveal the exact hidden potential ceilings of youth prospects before promoting them.", icon: <HiOutlineMagnifyingGlass /> },
    { title: "Pro Analytics & Wagon Wheels", desc: "Unlock elite analytical tools, interactive pitch maps, and wagon wheels to dissect opponent weaknesses.", icon: <HiOutlineChartPie /> },
    { title: "Automated Bidding", desc: "Never lose a bidding war. Set a max budget and let the system automatically bid on your behalf.", icon: <HiOutlineCurrencyDollar /> },
    { title: "Assistant Manager AI", desc: "Automatically rotate fatigued players and apply tactical presets to streamline your matchday prep.", icon: <HiOutlineCpuChip /> },
    { title: "Transfer Shortlisting", desc: "Scout like a pro. Pin your top transfer targets and monitor their market availability.", icon: <HiOutlineHeart /> },
    { title: "Stadium Naming Rights", desc: "Rebrand your home ground. Give your stadium a custom name for all visiting teams to see.", icon: <HiOutlineMapPin /> },
    { title: "Official Club Crest & Kits", desc: "Forge your club's legacy by uploading a bespoke team logo and customizing your jersey colors.", icon: <HiOutlineShieldCheck /> },
    { title: "Expanded Friendly Quota", desc: "Accelerate youth development by scheduling up to 5 friendly matches per day.", icon: <HiOutlinePlayCircle /> },
    { title: "Custom Tournaments", desc: "Design, host, and manage exclusive cup competitions for you and your rivals.", icon: <HiOutlineTrophy /> },
    { title: "Prime Time Fixtures", desc: "Command your schedule. Set custom start times for all your home league matches.", icon: <HiOutlineClock /> },
    { title: "Progression Tracking", desc: "Visualize success. Monitor player development over time with deep statistical graphs.", icon: <HiOutlineChartBar /> },
    { title: "Private Alliances", desc: "Coordinate tactics and build alliances with invite-only, private group chats.", icon: <HiOutlineChatBubbleLeftRight /> },
    { title: "Press & Media", desc: "Control the narrative. Publish official press releases and blogs to the CricketPlex community.", icon: <HiOutlineBookOpen /> },
    { title: "Custom Avatar", desc: "Establish your personal managerial identity with a custom profile picture.", icon: <HiOutlineUser /> },
  ];

  return (
    <div className="membership-page">
      <div className="membership-header">
        <HiOutlineSparkles className="membership-header-icon" />
        <div>
          <h1>CricketPlex Premium</h1>
          <p className="membership-subtitle">🚀 Upcoming Feature — The Ultimate Management Experience</p>
        </div>
      </div>

      <div className="membership-intro-card">
        <p>
          Step into the elite tier of cricket management. <strong>CricketPlex Premium</strong> unlocks a powerful suite of 
          advanced analytics, deep customization, and exclusive features designed to give you the ultimate competitive edge.
        </p>
      </div>

      <div className="membership-grid">
        {features.map((feature, index) => (
          <div key={index} className="membership-feature-card">
            <div className="membership-feature-icon">{feature.icon}</div>
            <div className="membership-feature-text">
              <h3>{feature.title}</h3>
              <p>{feature.desc}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="membership-footer">
        <p>© 2026 CricketPlex | All rights reserved.</p>
      </div>
    </div>
  );
};

export default Membership;