import { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  getActiveListings, getMyListings, placeBid, cancelListing,
} from '../../api/auth';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import toast from 'react-hot-toast';
import './TransferMarket.css';

const ROLE_SHORT = { BATSMAN: 'BAT', BOWLER: 'BOWL', ALL_ROUNDER: 'AR', KEEPER: 'WK' };
const ROLE_OPTIONS = ['BATSMAN', 'BOWLER', 'ALL_ROUNDER', 'KEEPER'];
const BAT_HAND_OPTIONS = ['RH', 'LH'];
const BOWL_TYPE_OPTIONS = ['FS', 'WS', 'F', 'M', 'FM', 'MF'];
const BOWL_TYPE_LABELS = { FS: 'Finger Spin', WS: 'Wrist Spin', F: 'Fast', M: 'Medium', FM: 'Fast Medium', MF: 'Medium Fast' };
const WS_URL = 'http://localhost:8080/ws';

function useCountdown(endStr) {
  const [remaining, setRemaining] = useState('');
  useEffect(() => {
    if (!endStr) { setRemaining(''); return; }
    const tick = () => {
      const diff = new Date(endStr) - Date.now();
      if (diff <= 0) { setRemaining('Ended'); return; }
      const h = Math.floor(diff / 3_600_000);
      const m = Math.floor((diff % 3_600_000) / 60_000);
      const s = Math.floor((diff % 60_000) / 1000);
      setRemaining(`${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`);
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [endStr]);
  return remaining;
}

export default function TransferMarket() {
  const navigate = useNavigate();
  const [tab, setTab] = useState('browse');
  const [listings, setListings] = useState([]);
  const [myListings, setMyListings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [bidInputs, setBidInputs] = useState({});
  const [bidding, setBidding] = useState(null);
  const [wsConnected, setWsConnected] = useState(false);
  const [myTeamId, setMyTeamId] = useState(null);
  const stompRef = useRef(null);

  const loadListings = useCallback(() => {
    setLoading(true);
    getActiveListings()
      .then(res => setListings(res.data))
      .catch(() => toast.error('Failed to load listings'))
      .finally(() => setLoading(false));
  }, []);

  const loadMyListings = useCallback(() => {
    getMyListings()
      .then(res => setMyListings(res.data))
      .catch(() => toast.error('Failed to load your listings'));
  }, []);

  // Apply real-time WebSocket updates to listings state
  const applyWsMessage = useCallback((msg) => {
    if (msg.type === 'BID_UPDATE') {
      setListings(prev => prev.map(l => {
        if (l.listingId !== msg.listingId) return l;
        const isLeading = myTeamId && msg.currentBidderTeamId === myTeamId;
        return {
          ...l,
          currentBid: msg.currentBid,
          currentBidderTeam: msg.currentBidderTeam,
          auctionEndsAt: msg.auctionEndsAt,
          minNextBid: msg.minNextBid,
          bidCount: msg.bidCount,
          isLeadingBidder: isLeading,
          hasBid: isLeading || l.hasBid,
        };
      }));
      // Also update my-listings if the bid is on one of our listings
      setMyListings(prev => prev.map(l =>
        l.listingId === msg.listingId ? {
          ...l,
          currentBid: msg.currentBid,
          currentBidderTeam: msg.currentBidderTeam,
          auctionEndsAt: msg.auctionEndsAt,
        } : l
      ));
    } else if (msg.type === 'AUCTION_COMPLETE') {
      // Remove completed auctions from browse, refresh my-listings
      setListings(prev => prev.filter(l => l.listingId !== msg.listingId));
      loadMyListings();
    }
  }, [loadMyListings, myTeamId]);

  // WebSocket connection
  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      onConnect: () => {
        setWsConnected(true);
        client.subscribe('/topic/transfer', (message) => {
          try {
            const data = JSON.parse(message.body);
            applyWsMessage(data);
          } catch { /* ignore malformed */ }
        });
      },
      onDisconnect: () => setWsConnected(false),
      onStompError: () => setWsConnected(false),
    });
    client.activate();
    stompRef.current = client;
    return () => { client.deactivate(); };
  }, [applyWsMessage]);

  // Initial load
  useEffect(() => { loadListings(); }, [loadListings]);
  useEffect(() => {
    if (tab === 'my-listings') loadMyListings();
  }, [tab, loadMyListings]);

  const handleBid = async (listingId) => {
    const listing = listings.find(l => l.listingId === listingId);
    const fallback = listing ? (listing.currentBidderTeam ? listing.minNextBid : listing.currentBid) : 0;
    const amount = Number(bidInputs[listingId] ?? fallback);
    if (!amount || amount <= 0) { toast.error('Enter a valid bid amount'); return; }
    setBidding(listingId);
    try {
      const res = await placeBid(listingId, amount);
      toast.success(res.data.message);
      if (res.data.myTeamId) setMyTeamId(res.data.myTeamId);
      // Mark this listing as leading bidder locally
      setListings(prev => prev.map(l =>
        l.listingId === listingId ? { ...l, isLeadingBidder: true, hasBid: true } : l
      ));
      setBidInputs(prev => { const next = { ...prev }; delete next[listingId]; return next; });
      loadListings();
    } catch (e) {
      toast.error(e.response?.data?.error || 'Bid failed');
    } finally {
      setBidding(null);
    }
  };

  const handleCancel = async (listingId) => {
    try {
      const res = await cancelListing(listingId);
      toast.success(res.data.message);
      loadListings();
      loadMyListings();
    } catch (e) {
      toast.error(e.response?.data?.error || 'Cancel failed');
    }
  };

  const tabs = [
    { key: 'browse', label: 'Browse Market' },
    { key: 'my-listings', label: 'My Listings' },
  ];

  return (
    <div className="tm-page">
      <div className="tm-header">
        <h1 className="tm-title">Transfer Market</h1>
        <span className={`tm-ws-indicator ${wsConnected ? 'connected' : ''}`}>
          {wsConnected ? 'LIVE' : 'CONNECTING'}
        </span>
      </div>

      <div className="tm-tabs">
        {tabs.map(t => (
          <button key={t.key} className={`tm-tab ${tab === t.key ? 'active' : ''}`}
            onClick={() => setTab(t.key)}>{t.label}</button>
        ))}
      </div>

      {tab === 'browse' && (
        <BrowseSection
          listings={listings} loading={loading}
          bidInputs={bidInputs} setBidInputs={setBidInputs}
          bidding={bidding} handleBid={handleBid}
          navigate={navigate}
        />
      )}

      {tab === 'my-listings' && (
        <MyListingsSection
          listings={myListings}
          handleCancel={handleCancel}
          navigate={navigate}
        />
      )}
    </div>
  );
}

function AuctionTimer({ endStr }) {
  const remaining = useCountdown(endStr);
  if (!remaining) return null;
  const isUrgent = remaining !== 'Ended' && !remaining.includes('h') && parseInt(remaining) <= 5;
  return (
    <span className={`tm-auction-timer ${remaining === 'Ended' ? 'ended' : ''} ${isUrgent ? 'urgent' : ''}`}>
      {remaining === 'Ended' ? 'Auction Ended' : remaining}
    </span>
  );
}

function BrowseSection({ listings, loading, bidInputs, setBidInputs, bidding, handleBid, navigate }) {
  const [showFilters, setShowFilters] = useState(false);
  const [filters, setFilters] = useState({
    name: '', country: '', role: '', batHand: '', bowlType: '',
    minAge: '', maxAge: '', minRating: '', maxRating: '',
    minBat: '', maxBat: '', minBowl: '', maxBowl: '',
  });

  const setF = (key, val) => setFilters(prev => ({ ...prev, [key]: val }));
  const clearFilters = () => setFilters({
    name: '', country: '', role: '', batHand: '', bowlType: '',
    minAge: '', maxAge: '', minRating: '', maxRating: '',
    minBat: '', maxBat: '', minBowl: '', maxBowl: '',
  });
  const hasFilters = Object.values(filters).some(v => v !== '');

  const filtered = listings.filter(l => {
    const p = l.player;
    if (filters.name && !p.name.toLowerCase().includes(filters.name.toLowerCase())) return false;
    if (filters.country && !p.country.toLowerCase().includes(filters.country.toLowerCase())) return false;
    if (filters.role && p.role !== filters.role) return false;
    if (filters.batHand && p.batHand !== filters.batHand) return false;
    if (filters.bowlType && p.bowlType !== filters.bowlType) return false;
    if (filters.minAge && p.age < Number(filters.minAge)) return false;
    if (filters.maxAge && p.age > Number(filters.maxAge)) return false;
    if (filters.minRating && p.rating < Number(filters.minRating)) return false;
    if (filters.maxRating && p.rating > Number(filters.maxRating)) return false;
    if (filters.minBat && p.batRating < Number(filters.minBat)) return false;
    if (filters.maxBat && p.batRating > Number(filters.maxBat)) return false;
    if (filters.minBowl && p.bowlRating < Number(filters.minBowl)) return false;
    if (filters.maxBowl && p.bowlRating > Number(filters.maxBowl)) return false;
    return true;
  });

  // Unique countries from current listings
  const countries = [...new Set(listings.map(l => l.player.country).filter(Boolean))].sort();

  if (loading) return <div className="tm-loading">Loading listings…</div>;
  if (!listings.length) return <div className="tm-empty">No players listed on the transfer market</div>;

  return (
    <>
      <div className="tm-filter-bar">
        <button className="tm-filter-toggle" onClick={() => setShowFilters(v => !v)}>
          {showFilters ? 'Hide Filters' : 'Filters'} {hasFilters && `(${filtered.length}/${listings.length})`}
        </button>
        {hasFilters && <button className="tm-filter-clear" onClick={clearFilters}>Clear All</button>}
      </div>

      {showFilters && (
        <div className="tm-filters">
          <div className="tm-filter-group">
            <label className="tm-filter-label">Name</label>
            <input className="tm-filter-input" placeholder="Search name…" value={filters.name} onChange={e => setF('name', e.target.value)} />
          </div>
          <div className="tm-filter-group">
            <label className="tm-filter-label">Country</label>
            <select className="tm-filter-select" value={filters.country} onChange={e => setF('country', e.target.value)}>
              <option value="">All</option>
              {countries.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div className="tm-filter-group">
            <label className="tm-filter-label">Role</label>
            <select className="tm-filter-select" value={filters.role} onChange={e => setF('role', e.target.value)}>
              <option value="">All</option>
              {ROLE_OPTIONS.map(r => <option key={r} value={r}>{ROLE_SHORT[r]}</option>)}
            </select>
          </div>
          <div className="tm-filter-group">
            <label className="tm-filter-label">Bat Hand</label>
            <select className="tm-filter-select" value={filters.batHand} onChange={e => setF('batHand', e.target.value)}>
              <option value="">All</option>
              {BAT_HAND_OPTIONS.map(h => <option key={h} value={h}>{h === 'RH' ? 'Right' : 'Left'}</option>)}
            </select>
          </div>
          <div className="tm-filter-group">
            <label className="tm-filter-label">Bowl Type</label>
            <select className="tm-filter-select" value={filters.bowlType} onChange={e => setF('bowlType', e.target.value)}>
              <option value="">All</option>
              {BOWL_TYPE_OPTIONS.map(t => <option key={t} value={t}>{BOWL_TYPE_LABELS[t]}</option>)}
            </select>
          </div>
          <div className="tm-filter-group">
            <label className="tm-filter-label">Age</label>
            <div className="tm-filter-range">
              <input className="tm-filter-input sm" type="number" placeholder="Min" value={filters.minAge} onChange={e => setF('minAge', e.target.value)} />
              <span className="tm-filter-sep">–</span>
              <input className="tm-filter-input sm" type="number" placeholder="Max" value={filters.maxAge} onChange={e => setF('maxAge', e.target.value)} />
            </div>
          </div>
          <div className="tm-filter-group">
            <label className="tm-filter-label">Rating</label>
            <div className="tm-filter-range">
              <input className="tm-filter-input sm" type="number" placeholder="Min" value={filters.minRating} onChange={e => setF('minRating', e.target.value)} />
              <span className="tm-filter-sep">–</span>
              <input className="tm-filter-input sm" type="number" placeholder="Max" value={filters.maxRating} onChange={e => setF('maxRating', e.target.value)} />
            </div>
          </div>
          <div className="tm-filter-group">
            <label className="tm-filter-label">BAT Skill</label>
            <div className="tm-filter-range">
              <input className="tm-filter-input sm" type="number" placeholder="Min" value={filters.minBat} onChange={e => setF('minBat', e.target.value)} />
              <span className="tm-filter-sep">–</span>
              <input className="tm-filter-input sm" type="number" placeholder="Max" value={filters.maxBat} onChange={e => setF('maxBat', e.target.value)} />
            </div>
          </div>
          <div className="tm-filter-group">
            <label className="tm-filter-label">BOWL Skill</label>
            <div className="tm-filter-range">
              <input className="tm-filter-input sm" type="number" placeholder="Min" value={filters.minBowl} onChange={e => setF('minBowl', e.target.value)} />
              <span className="tm-filter-sep">–</span>
              <input className="tm-filter-input sm" type="number" placeholder="Max" value={filters.maxBowl} onChange={e => setF('maxBowl', e.target.value)} />
            </div>
          </div>
        </div>
      )}

      {filtered.length === 0 && <div className="tm-empty">No players match your filters</div>}

      <div className="tm-listings">
        {filtered.map(l => (
        <div key={l.listingId} className={`tm-card${l.isLeadingBidder ? ' tm-card-leading' : l.hasBid ? ' tm-card-outbid' : ''}`}>
          <div className="tm-card-top">
            <div className="tm-player-info" onClick={() => navigate(`/player/${l.player.id}`)}>
              <span className="tm-player-name">{l.player.name}</span>
              <span className="tm-player-role">{ROLE_SHORT[l.player.role]}</span>
              <span className="tm-player-age">Age {l.player.age}</span>
              <span className="tm-player-country">{l.player.country}</span>
            </div>
            <div className="tm-player-rating">{l.player.rating}</div>
          </div>

          <div className="tm-ratings-split">
            <div className="tm-ratings-col">
              <RatingBar label="BAT" value={l.player.batRating} color="#22c55e" />
              <RatingBar label="BOWL" value={l.player.bowlRating} color="#3b82f6" />
              <RatingBar label="WK" value={l.player.keeperRating} color="#f59e0b" />
              <RatingBar label="FLD" value={l.player.fldRating} color="#8b5cf6" />
            </div>
            <div className="tm-ratings-divider" />
            <div className="tm-ratings-col">
              <RatingBar label="STA" value={l.player.stamina} color="#fb923c" />
              <RatingBar label="EXP" value={l.player.experience} color="#22d3ee" />
              <RatingBar label="CONF" value={l.player.confidence} color="#f472b6" />
              <RatingBar label="FIT" value={l.player.fitness} color="#a3e635" />
            </div>
          </div>

          <div className="tm-card-market">
            <div className="tm-price-row">
              <span className="tm-price-label">Current Bid</span>
              <span className="tm-price-value highlight">${l.currentBid?.toLocaleString()}</span>
            </div>
            {l.currentBidderTeam && (
              <div className="tm-price-row">
                <span className="tm-price-label">Leading</span>
                <span className="tm-price-value">{l.currentBidderTeam}</span>
              </div>
            )}
            <div className="tm-price-row">
              <span className="tm-price-label">Min Next Bid</span>
              <span className="tm-price-value">${l.minNextBid?.toLocaleString()}</span>
            </div>
            <div className="tm-price-row">
              <span className="tm-price-label">Bids</span>
              <span className="tm-price-value">{l.bidCount}</span>
            </div>
            <div className="tm-price-row">
              <span className="tm-price-label">Ends In</span>
              <AuctionTimer endStr={l.auctionEndsAt} />
            </div>
            <div className="tm-price-row">
              <span className="tm-price-label">Seller</span>
              <span className="tm-seller-link" onClick={(e) => { e.stopPropagation(); navigate(`/team/${l.sellerTeamId}`); }}>{l.sellerTeam}</span>
            </div>
          </div>

          {!l.isOwn && (
            <div className="tm-bid-row">
              <input
                className="tm-bid-input"
                type="number"
                placeholder={`Min $${(l.currentBidderTeam ? l.minNextBid : l.currentBid)?.toLocaleString()}`}
                value={bidInputs[l.listingId] ?? (l.currentBidderTeam ? l.minNextBid : l.currentBid)}
                onChange={e => setBidInputs(prev => ({ ...prev, [l.listingId]: e.target.value }))}
                min={l.currentBidderTeam ? l.minNextBid : l.currentBid}
              />
              <button
                className="tm-bid-btn"
                onClick={() => handleBid(l.listingId)}
                disabled={bidding === l.listingId}
              >{bidding === l.listingId ? 'Bidding…' : 'Place Bid'}</button>
            </div>
          )}

          {l.isOwn && <div className="tm-own-badge">Your Listing</div>}
        </div>
      ))}
    </div>
    </>
  );
}

function MyListingsSection({ listings, handleCancel, navigate }) {
  if (!listings.length) return <div className="tm-empty">You have no listings</div>;

  return (
    <div className="tm-listings">
      {listings.map(l => (
        <div key={l.listingId} className={`tm-card ${l.status !== 'ACTIVE' ? 'tm-card-inactive' : ''}`}>
          <div className="tm-card-top">
            <div className="tm-player-info" onClick={() => navigate(`/player/${l.player.id}`)}>
              <span className="tm-player-name">{l.player.name}</span>
              <span className="tm-player-role">{ROLE_SHORT[l.player.role]}</span>
              <span className="tm-player-age">Age {l.player.age}</span>
            </div>
            <span className={`tm-status-badge ${l.status.toLowerCase()}`}>{l.status}</span>
          </div>

          <div className="tm-card-market">
            <div className="tm-price-row">
              <span className="tm-price-label">Current Bid</span>
              <span className="tm-price-value highlight">${l.currentBid?.toLocaleString()}</span>
            </div>
            {l.currentBidderTeam && (
              <div className="tm-price-row">
                <span className="tm-price-label">Leading Bidder</span>
                <span className="tm-price-value">{l.currentBidderTeam}</span>
              </div>
            )}
            {l.status === 'ACTIVE' && (
              <div className="tm-price-row">
                <span className="tm-price-label">Ends In</span>
                <AuctionTimer endStr={l.auctionEndsAt} />
              </div>
            )}
            <div className="tm-price-row">
              <span className="tm-price-label">Listing Fee</span>
              <span className="tm-price-value">${l.listingFee?.toLocaleString()}</span>
            </div>
            <div className="tm-price-row">
              <span className="tm-price-label">TM Tax</span>
              <span className="tm-price-value">${l.tmTax?.toLocaleString()}</span>
            </div>
            {l.salePrice && (
              <div className="tm-price-row">
                <span className="tm-price-label">Sold For</span>
                <span className="tm-price-value highlight">${l.salePrice?.toLocaleString()}</span>
              </div>
            )}
          </div>

          {l.status === 'ACTIVE' && l.bids?.length > 0 && (
            <div className="tm-bids-section">
              <h4 className="tm-bids-title">Bids ({l.bids.length})</h4>
              {l.bids.map((b, i) => (
                <div key={b.bidId} className="tm-bid-item">
                  <span className="tm-bid-team">{b.bidderTeam}</span>
                  <span className="tm-bid-amount">${b.bidAmount?.toLocaleString()}</span>
                  {b.bidAt && <span className="tm-bid-date">{new Date(b.bidAt).toLocaleDateString()}</span>}
                  {i === 0 && <span className="tm-bid-highest">Highest</span>}
                </div>
              ))}
              <div className="tm-auction-note">Auction auto-completes when timer expires</div>
            </div>
          )}

          {l.status === 'ACTIVE' && (!l.bids || l.bids.length === 0) && (
            <div className="tm-no-bids">No bids yet — auction expires when timer ends</div>
          )}

          {l.status === 'ACTIVE' && (
            <button className="tm-cancel-btn" onClick={() => handleCancel(l.listingId)}>
              Cancel Listing
            </button>
          )}
        </div>
      ))}
    </div>
  );
}

function RatingBar({ label, value, color }) {
  const pct = Math.min((value / 100) * 100, 100);
  return (
    <div className="tm-rating-row">
      <span className="tm-rating-label">{label}</span>
      <div className="tm-rating-track">
        <div className="tm-rating-fill" style={{ width: `${pct}%`, background: color }} />
      </div>
      <span className="tm-rating-value">{value}</span>
    </div>
  );
}
