import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import toast from 'react-hot-toast';
import {
  HiOutlineSparkles,
  HiOutlineUser,
  HiOutlineClock,
  HiOutlineTrophy,
  HiOutlineChatBubbleLeftRight,
  HiOutlineChartBar,
  HiOutlinePlayCircle,
  HiOutlineHeart,
  HiOutlineCurrencyDollar,
  HiOutlineBookOpen,
  HiOutlineGlobeAlt,
  HiOutlineMagnifyingGlass,
  HiOutlineChartPie,
  HiOutlineCpuChip,
  HiOutlineMapPin,
  HiOutlineShieldCheck,
} from "react-icons/hi2";
import {
  createRazorpayOrder,
  createPayPalOrder,
  capturePayPalOrder,
  createStripePayment,
  confirmStripePayment,
  getIndianSupporterPlans,
  getGlobalSupporterPlans,
  getSupporterStatus,
  verifyRazorpayPayment,
} from '../../api/auth';
import { useAuth } from '../../context/AuthContext';
import "./Membership.css";

/** PayPal Smart Buttons — rendered once on mount for the selected plan. */
function PayPalButtonsComponent({ planCode, onSuccess }) {
  const containerRef = useRef(null);

  useEffect(() => {
    if (!containerRef.current || !window.paypal) return;

    const buttons = window.paypal.Buttons({
      style: { layout: 'vertical', color: 'gold', shape: 'rect', label: 'pay' },
      createOrder: async () => {
        const res = await createPayPalOrder(planCode);
        return res.data.orderId;
      },
      onApprove: async (data) => {
        try {
          await onSuccess(data.orderID);
        } catch {
          /* handled by parent */
        }
      },
      onError: () => toast.error('PayPal payment error. Please try again.'),
      onCancel: () => toast('Payment cancelled.'),
    });

    buttons.render(containerRef.current);

    return () => {
      if (containerRef.current) containerRef.current.innerHTML = '';
    };
  }, [planCode]); // eslint-disable-line react-hooks/exhaustive-deps

  return <div ref={containerRef} className="membership-paypal-btn-wrap" />;
}

/** Stripe Card Element form. */
function StripeCheckoutComponent({ clientSecret, publishableKey, onSuccess }) {
  const cardRef = useRef(null);
  const stripeRef = useRef(null);
  const cardElRef = useRef(null);
  const [ready, setReady] = useState(false);
  const [paying, setPaying] = useState(false);
  const [cardError, setCardError] = useState('');

  useEffect(() => {
    if (!cardRef.current || !window.Stripe || !publishableKey) return;

    const stripe = window.Stripe(publishableKey);
    const elements = stripe.elements();
    const card = elements.create('card', {
      style: {
        base: { fontSize: '15px', color: '#e2e8f0', '::placeholder': { color: '#64748b' }, iconColor: '#64748b' },
        invalid: { color: '#f87171' },
      },
    });
    card.mount(cardRef.current);
    card.on('ready', () => setReady(true));
    card.on('change', (e) => setCardError(e.error?.message || ''));

    stripeRef.current = stripe;
    cardElRef.current = card;

    return () => card.unmount();
  }, [publishableKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const handlePay = async () => {
    if (!stripeRef.current || !cardElRef.current) return;
    setPaying(true);
    setCardError('');
    try {
      const { error, paymentIntent } = await stripeRef.current.confirmCardPayment(clientSecret, {
        payment_method: { card: cardElRef.current },
      });
      if (error) {
        setCardError(error.message || 'Payment failed');
      } else if (paymentIntent?.status === 'succeeded') {
        await onSuccess(paymentIntent.id);
      }
    } catch {
      setCardError('Payment processing error. Please try again.');
    } finally {
      setPaying(false);
    }
  };

  return (
    <div className="membership-stripe-wrap">
      <div ref={cardRef} className="membership-stripe-card-element" />
      {cardError && <div className="membership-stripe-error">{cardError}</div>}
      <button
        className="membership-buy-btn membership-stripe-pay-btn"
        onClick={handlePay}
        disabled={!ready || paying}
      >
        {paying ? 'Processing...' : 'Pay with Card'}
      </button>
    </div>
  );
}

const Membership = () => {
  const { user, refreshUser } = useAuth();

  // ── India / Razorpay state ────────────────────────────────────────────────
  const [activeTab, setActiveTab] = useState('india');
  const [plans, setPlans] = useState([]);
  const [status, setStatus] = useState(null);
  const [loadingPlans, setLoadingPlans] = useState(true);
  const [payingPlanCode, setPayingPlanCode] = useState('');

  // ── International / PayPal + Stripe state ────────────────────────────────
  const [globalPlans, setGlobalPlans] = useState([]);
  const [paypalClientId, setPaypalClientId] = useState('');
  const [stripePublishableKey, setStripePublishableKey] = useState('');
  const [paypalLoaded, setPaypalLoaded] = useState(false);
  const [stripeLoaded, setStripeLoaded] = useState(false);
  const [globalPlansLoaded, setGlobalPlansLoaded] = useState(false);
  const [loadingGlobalPlans, setLoadingGlobalPlans] = useState(false);
  const [selectedIntlPlan, setSelectedIntlPlan] = useState(null);
  const [intlPaymentMethod, setIntlPaymentMethod] = useState(null); // 'paypal' | 'stripe'
  const [stripeClientSecret, setStripeClientSecret] = useState('');
  const [loadingStripeIntent, setLoadingStripeIntent] = useState(false);
  const [capturingPayPal, setCapturingPayPal] = useState(false);
  const [capturingStripe, setCapturingStripe] = useState(false);

  // ── Razorpay helpers ──────────────────────────────────────────────────────
  const loadRazorpayScript = () =>
    new Promise((resolve) => {
      if (window.Razorpay) { resolve(true); return; }
      const script = document.createElement('script');
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.onload = () => resolve(true);
      script.onerror = () => resolve(false);
      document.body.appendChild(script);
    });

  const loadMembershipData = useCallback(async () => {
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
  }, []);

  useEffect(() => {
    loadMembershipData();
  }, [loadMembershipData]);

  // ── Load global (PayPal + Stripe) plans lazily when switching tab ────────
  useEffect(() => {
    if (activeTab !== 'international' || globalPlansLoaded) return;
    const load = async () => {
      setLoadingGlobalPlans(true);
      try {
        const res = await getGlobalSupporterPlans();
        setGlobalPlans(res.data.plans || []);
        const cid = res.data.paypalClientId || '';
        const spk = res.data.stripePublishableKey || '';
        setPaypalClientId(cid);
        setStripePublishableKey(spk);

        if (cid && !window.paypal) {
          const s = document.createElement('script');
          s.src = `https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(cid)}&currency=USD`;
          s.onload = () => setPaypalLoaded(true);
          s.onerror = () => toast.error('Failed to load PayPal SDK');
          document.body.appendChild(s);
        } else if (window.paypal) {
          setPaypalLoaded(true);
        }

        if (spk && !window.Stripe) {
          const s = document.createElement('script');
          s.src = 'https://js.stripe.com/v3/';
          s.onload = () => setStripeLoaded(true);
          s.onerror = () => toast.error('Failed to load Stripe SDK');
          document.body.appendChild(s);
        } else if (window.Stripe) {
          setStripeLoaded(true);
        }
      } catch {
        toast.error('Failed to load international plans');
      } finally {
        setLoadingGlobalPlans(false);
        setGlobalPlansLoaded(true);
      }
    };
    load();
  }, [activeTab, globalPlansLoaded]);

  // ── Create Stripe PaymentIntent when user picks Stripe for a plan ─────────
  useEffect(() => {
    if (intlPaymentMethod !== 'stripe' || !selectedIntlPlan) return;
    let cancelled = false;
    const create = async () => {
      setLoadingStripeIntent(true);
      setStripeClientSecret('');
      try {
        const res = await createStripePayment(selectedIntlPlan.code);
        if (!cancelled) setStripeClientSecret(res.data.clientSecret);
      } catch (err) {
        if (!cancelled) {
          toast.error(err?.response?.data?.message || 'Unable to initiate Stripe payment');
          setIntlPaymentMethod(null);
        }
      } finally {
        if (!cancelled) setLoadingStripeIntent(false);
      }
    };
    create();
    return () => { cancelled = true; };
  }, [intlPaymentMethod, selectedIntlPlan?.code]); // eslint-disable-line react-hooks/exhaustive-deps

  const currentPlanLabel = useMemo(() => {
    if (!status?.planCode) return '';
    const current = plans.find((p) => p.code === status.planCode);
    return current?.label || status.planCode;
  }, [plans, status]);

  const handlePayWithRazorpay = async (planCode) => {
    setPayingPlanCode(planCode);
    try {
      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded) { toast.error('Unable to load Razorpay checkout'); return; }

      const orderRes = await createRazorpayOrder(planCode);
      const order = orderRes.data;

      const options = {
        key: order.key,
        amount: order.amount,
        currency: order.currency,
        name: 'CricketPlex Premium',
        description: `Supporter plan: ${planCode}`,
        order_id: order.orderId,
        prefill: { name: user?.name || '', email: user?.email || '' },
        notes: { app: 'CricketPlex', planCode },
        theme: { color: '#0ea5e9' },
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
      razorpay.on('payment.failed', (response) => {
        toast.error(response?.error?.description || 'Payment failed');
      });
      razorpay.open();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Unable to initiate payment');
    } finally {
      setPayingPlanCode('');
    }
  };

  const handlePayPalCapture = async (orderId) => {
    setCapturingPayPal(true);
    try {
      await capturePayPalOrder(orderId);
      toast.success('Supporter membership activated!');
      await refreshUser();
      await loadMembershipData();
      setSelectedIntlPlan(null);
      setIntlPaymentMethod(null);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Payment capture failed');
    } finally {
      setCapturingPayPal(false);
    }
  };

  const handleStripeConfirm = async (paymentIntentId) => {
    setCapturingStripe(true);
    try {
      await confirmStripePayment(paymentIntentId);
      toast.success('Supporter membership activated!');
      await refreshUser();
      await loadMembershipData();
      setSelectedIntlPlan(null);
      setIntlPaymentMethod(null);
      setStripeClientSecret('');
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Payment confirmation failed');
    } finally {
      setCapturingStripe(false);
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
        {/* Tab switcher */}
        <div className="membership-tabs">
          <button
            className={`membership-tab-btn${activeTab === 'india' ? ' active' : ''}`}
            onClick={() => { setActiveTab('india'); setSelectedIntlPlan(null); setIntlPaymentMethod(null); setStripeClientSecret(''); }}
          >
            India — ₹ (Razorpay)
          </button>
          <button
            className={`membership-tab-btn${activeTab === 'international' ? ' active' : ''}`}
            onClick={() => setActiveTab('international')}
          >
            International — $ (PayPal)
          </button>
        </div>

        {/* ── India / Razorpay tab ── */}
        {activeTab === 'india' && (
          <>
            <div className="membership-plans-head">
              <h2>India Plans</h2>
              <p>Pay securely with UPI, cards, netbanking, or wallet via Razorpay.</p>
            </div>

            {status?.isSupporter && status?.provider !== 'PAYPAL' && (
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
                  const isCurrent = status?.isSupporter && status?.planCode === plan.code && status?.provider !== 'PAYPAL';
                  const isPaying = payingPlanCode === plan.code;
                  return (
                    <div className={`membership-plan-item${isCurrent ? ' active' : ''}`} key={plan.code}>
                      <div className="membership-plan-title">{plan.label}</div>
                      <div className="membership-plan-price">₹{plan.amount}</div>
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
          </>
        )}

        {/* ── International / PayPal + Stripe tab ── */}
        {activeTab === 'international' && (
          <>
            <div className="membership-plans-head">
              <h2>International Plans</h2>
              <p>Pay securely in USD — choose PayPal or Card (Stripe).</p>
            </div>

            {status?.isSupporter && (status?.provider === 'PAYPAL' || status?.provider === 'STRIPE') && (
              <div className="membership-active-chip">
                Active Plan: <strong>{status.planCode}</strong> via {status.provider}
                {status.supporterUntil ? ` (valid till ${new Date(status.supporterUntil).toLocaleDateString()})` : ''}
              </div>
            )}

            {loadingGlobalPlans ? (
              <div className="membership-loading">Loading international plans...</div>
            ) : (!paypalClientId && !stripePublishableKey) ? (
              <div className="membership-no-paypal">
                International payments are not yet available. Check back soon!
              </div>
            ) : (
              <>
                <p className="membership-plan-select-hint">Select a plan to continue.</p>
                <div className="membership-plan-grid">
                  {globalPlans.map((plan) => {
                    const isCurrent = status?.isSupporter && status?.planCode === plan.code
                      && (status?.provider === 'PAYPAL' || status?.provider === 'STRIPE');
                    const isSelected = selectedIntlPlan?.code === plan.code;
                    return (
                      <div
                        key={plan.code}
                        className={`membership-plan-item${isCurrent ? ' active' : ''}${isSelected ? ' selected' : ''}`}
                        onClick={() => {
                          if (isSelected) { setSelectedIntlPlan(null); setIntlPaymentMethod(null); setStripeClientSecret(''); }
                          else { setSelectedIntlPlan(plan); setIntlPaymentMethod(null); setStripeClientSecret(''); }
                        }}
                        role="button"
                        tabIndex={0}
                        onKeyDown={(e) => e.key === 'Enter' && (isSelected
                          ? (setSelectedIntlPlan(null), setIntlPaymentMethod(null))
                          : setSelectedIntlPlan(plan))}
                      >
                        <div className="membership-plan-title">{plan.label}</div>
                        <div className="membership-plan-price">${plan.amount}</div>
                        <div className="membership-plan-select-label">
                          {isSelected ? 'Selected ✓' : isCurrent ? 'Current plan' : 'Select'}
                        </div>
                      </div>
                    );
                  })}
                </div>

                {selectedIntlPlan && (
                  <div className="membership-paypal-checkout">
                    <div className="membership-paypal-checkout-title">
                      {selectedIntlPlan.label} — ${selectedIntlPlan.amount} USD
                    </div>

                    {/* Payment method picker */}
                    <div className="membership-intl-methods">
                      {paypalClientId && (
                        <button
                          className={`membership-method-btn${intlPaymentMethod === 'paypal' ? ' active' : ''}`}
                          onClick={() => { setIntlPaymentMethod('paypal'); setStripeClientSecret(''); }}
                        >
                          PayPal
                        </button>
                      )}
                      {stripePublishableKey && (
                        <button
                          className={`membership-method-btn${intlPaymentMethod === 'stripe' ? ' active' : ''}`}
                          onClick={() => setIntlPaymentMethod('stripe')}
                        >
                          Card (Stripe)
                        </button>
                      )}
                    </div>

                    {/* PayPal */}
                    {intlPaymentMethod === 'paypal' && (
                      capturingPayPal ? (
                        <div className="membership-loading">Confirming payment...</div>
                      ) : paypalLoaded ? (
                        <PayPalButtonsComponent
                          key={selectedIntlPlan.code}
                          planCode={selectedIntlPlan.code}
                          onSuccess={handlePayPalCapture}
                        />
                      ) : (
                        <div className="membership-loading">Loading PayPal...</div>
                      )
                    )}

                    {/* Stripe */}
                    {intlPaymentMethod === 'stripe' && (
                      loadingStripeIntent ? (
                        <div className="membership-loading">Preparing secure payment...</div>
                      ) : stripeClientSecret && stripeLoaded ? (
                        capturingStripe ? (
                          <div className="membership-loading">Confirming payment...</div>
                        ) : (
                          <StripeCheckoutComponent
                            key={selectedIntlPlan.code}
                            clientSecret={stripeClientSecret}
                            publishableKey={stripePublishableKey}
                            onSuccess={handleStripeConfirm}
                          />
                        )
                      ) : !stripeLoaded ? (
                        <div className="membership-loading">Loading Stripe...</div>
                      ) : null
                    )}
                  </div>
                )}
              </>
            )}
          </>
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