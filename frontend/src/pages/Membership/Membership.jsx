import React, { useEffect, useMemo, useState } from "react";
import toast from 'react-hot-toast';
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
import {
  createRazorpayOrder,
  getIndianSupporterPlans,
  getSupporterStatus,
  verifyRazorpayPayment,
} from '../../api/auth';
import { useAuth } from '../../context/AuthContext';
import "./Membership.css";

const Membership = () => {
  const { user, refreshUser } = useAuth();
  const [plans, setPlans] = useState([]);
  const [status, setStatus] = useState(null);
  const [loadingPlans, setLoadingPlans] = useState(true);
  const [payingPlanCode, setPayingPlanCode] = useState('');

  const loadRazorpayScript = () =>
    new Promise((resolve) => {
      if (window.Razorpay) {
        resolve(true);
        return;
      }
      const script = document.createElement('script');
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.onload = () => resolve(true);
      script.onerror = () => resolve(false);
      document.body.appendChild(script);
    });

  const loadMembershipData = async () => {
    setLoadingPlans(true);
    try {
      const [plansRes, statusRes] = await Promise.all([
        getIndianSupporterPlans(),
        getSupporterStatus(),
      ]);
      setPlans(plansRes.data || []);
      setStatus(statusRes.data || null);
    } catch {
      toast.error('Failed to load supporter plans');
    } finally {
      setLoadingPlans(false);
    }
  };

  useEffect(() => {
    loadMembershipData();
  }, []);

  const currentPlanLabel = useMemo(() => {
    if (!status?.planCode) return '';
    const current = plans.find((p) => p.code === status.planCode);
    return current?.label || status.planCode;
  }, [plans, status]);

  const handlePayWithRazorpay = async (planCode) => {
    setPayingPlanCode(planCode);
    try {
      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded) {
        toast.error('Unable to load Razorpay checkout');
        return;
      }

      const orderRes = await createRazorpayOrder(planCode);
      const order = orderRes.data;

      const options = {
        key: order.key,
        amount: order.amount,
        currency: order.currency,
        name: 'CricketPlex Premium',
        description: `Supporter plan: ${planCode}`,
        order_id: order.orderId,
        prefill: {
          name: user?.name || '',
          email: user?.email || '',
        },
        notes: {
          app: 'CricketPlex',
          planCode,
        },
        theme: {
          color: '#0ea5e9',
        },
        handler: async function (response) {
          try {
            await verifyRazorpayPayment({
              planCode,
              orderId: response.razorpay_order_id,
              paymentId: response.razorpay_payment_id,
              signature: response.razorpay_signature,
            });
            toast.success('Supporter membership activated!');
            await refreshUser();
            await loadMembershipData();
          } catch (err) {
            toast.error(err?.response?.data?.message || 'Payment verification failed');
          }
        },
      };

      const razorpay = new window.Razorpay(options);
      razorpay.on('payment.failed', function (response) {
        toast.error(response?.error?.description || 'Payment failed');
      });
      razorpay.open();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Unable to initiate payment');
    } finally {
      setPayingPlanCode('');
    }
  };

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
          <p className="membership-subtitle">Unlock supporter-only features and manage a second team.</p>
        </div>
      </div>

      <div className="membership-plans-card">
        <div className="membership-plans-head">
          <h2>India Plans (Razorpay)</h2>
          <p>Choose a plan and pay securely with UPI, cards, netbanking, or wallet.</p>
        </div>

        {status?.isSupporter && (
          <div className="membership-active-chip">
            Active Plan: <strong>{currentPlanLabel}</strong>
            {status.supporterUntil ? ` (valid till ${new Date(status.supporterUntil).toLocaleDateString()})` : ''}
          </div>
        )}

        {loadingPlans ? (
          <div className="membership-loading">Loading plans...</div>
        ) : (
          <div className="membership-plan-grid">
            {plans.map((plan) => {
              const isCurrent = status?.isSupporter && status?.planCode === plan.code;
              const isPaying = payingPlanCode === plan.code;
              return (
                <div className={`membership-plan-item ${isCurrent ? 'active' : ''}`} key={plan.code}>
                  <div className="membership-plan-title">{plan.label}</div>
                  <div className="membership-plan-price">Rs {plan.amount}</div>
                  <button
                    className="membership-buy-btn"
                    disabled={isPaying}
                    onClick={() => handlePayWithRazorpay(plan.code)}
                  >
                    {isPaying ? 'Opening checkout...' : isCurrent ? 'Renew / Upgrade' : 'Pay with Razorpay'}
                  </button>
                </div>
              );
            })}
          </div>
        )}
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