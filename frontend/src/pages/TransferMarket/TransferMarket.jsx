import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  getActiveListings, getMyListings, placeBid, acceptBid, cancelListing,
} from '../../api/auth';
import toast from 'react-hot-toast';
import './TransferMarket.css';

const ROLE_SHORT = { BATSMAN: 'BAT', BOWLER: 'BOWL', ALL_ROUNDER: 'AR', KEEPER: 'WK' };

export default function TransferMarket() {
  const navigate = useNavigate();
  const [tab, setTab] = useState('browse');
  const [listings, setListings] = useState([]);
  const [myListings, setMyListings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [bidInputs, setBidInputs] = useState({});
  const [bidding, setBidding] = useState(null);

  const loadListings = () => {
    setLoading(true);
    getActiveListings()
      .then(res => setListings(res.data))
      .catch(() => toast.error('Failed to load listings'))
      .finally(() => setLoading(false));
  };

  const loadMyListings = () => {
    getMyListings()
      .then(res => setMyListings(res.data))
      .catch(() => toast.error('Failed to load your listings'));
  };

  useEffect(() => { loadListings(); }, []);
  useEffect(() => {
    if (tab === 'my-listings' && myListings.length === 0) loadMyListings();
  }, [tab]);

  const handleBid = async (listingId) => {
    const amount = Number(bidInputs[listingId]);
    if (!amount || amount <= 0) { toast.error('Enter a valid bid amount'); return; }
    setBidding(listingId);
    try {
      const res = await placeBid(listingId, amount);
      toast.success(res.data.message);
      setBidInputs(prev => ({ ...prev, [listingId]: '' }));
      loadListings();
    } catch (e) {
      toast.error(e.response?.data?.error || 'Bid failed');
    } finally {
      setBidding(null);
    }
  };

  const handleAccept = async (listingId) => {
    try {
      const res = await acceptBid(listingId);
      toast.success(res.data.message);
      loadListings();
      loadMyListings();
    } catch (e) {
      toast.error(e.response?.data?.error || 'Accept failed');
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
          handleAccept={handleAccept}
          handleCancel={handleCancel}
          navigate={navigate}
        />
      )}
    </div>
  );
}

function BrowseSection({ listings, loading, bidInputs, setBidInputs, bidding, handleBid, navigate }) {
  if (loading) return <div className="tm-loading">Loading listings…</div>;
  if (!listings.length) return <div className="tm-empty">No players listed on the transfer market</div>;

  return (
    <div className="tm-listings">
      {listings.map(l => (
        <div key={l.listingId} className="tm-card">
          <div className="tm-card-top">
            <div className="tm-player-info" onClick={() => navigate(`/player/${l.player.id}`)}>
              <span className="tm-player-name">{l.player.name}</span>
              <span className="tm-player-role">{ROLE_SHORT[l.player.role]}</span>
              <span className="tm-player-age">Age {l.player.age}</span>
              <span className="tm-player-country">{l.player.country}</span>
            </div>
            <div className="tm-player-rating">{l.player.rating}</div>
          </div>

          <div className="tm-card-stats">
            <MiniStat label="BAT" value={l.player.batRating} />
            <MiniStat label="BOWL" value={l.player.bowlRating} />
            {l.player.keeperRating > 0 && <MiniStat label="WK" value={l.player.keeperRating} />}
            <MiniStat label="FLD" value={l.player.fldRating} />
            <MiniStat label="STA" value={l.player.stamina} />
          </div>

          <div className="tm-card-market">
            <div className="tm-price-row">
              <span className="tm-price-label">Market Value</span>
              <span className="tm-price-value">${l.marketValue?.toLocaleString()}</span>
            </div>
            {l.highestBid && (
              <div className="tm-price-row">
                <span className="tm-price-label">Highest Bid</span>
                <span className="tm-price-value highlight">${l.highestBid?.toLocaleString()}</span>
              </div>
            )}
            <div className="tm-price-row">
              <span className="tm-price-label">Bids</span>
              <span className="tm-price-value">{l.bidCount}</span>
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
                placeholder={`Min $${l.marketValue?.toLocaleString()}`}
                value={bidInputs[l.listingId] || ''}
                onChange={e => setBidInputs(prev => ({ ...prev, [l.listingId]: e.target.value }))}
                min={l.highestBid ? l.highestBid + 1 : l.marketValue}
              />
              <button
                className="tm-bid-btn"
                onClick={() => handleBid(l.listingId)}
                disabled={bidding === l.listingId}
              >{bidding === l.listingId ? 'Bidding…' : 'Place Bid'}</button>
            </div>
          )}

          {l.isOwn && <div className="tm-own-badge">Your Listing</div>}

          {l.listedAt && (
            <div className="tm-card-date">Listed {new Date(l.listedAt).toLocaleDateString()}</div>
          )}
        </div>
      ))}
    </div>
  );
}

function MyListingsSection({ listings, handleAccept, handleCancel, navigate }) {
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
              <span className="tm-price-label">Market Value</span>
              <span className="tm-price-value">${l.marketValue?.toLocaleString()}</span>
            </div>
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
              <button className="tm-accept-btn" onClick={() => handleAccept(l.listingId)}>
                Accept Highest Bid (${l.bids[0]?.bidAmount?.toLocaleString()})
              </button>
            </div>
          )}

          {l.status === 'ACTIVE' && (!l.bids || l.bids.length === 0) && (
            <div className="tm-no-bids">No bids yet</div>
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

function MiniStat({ label, value }) {
  return (
    <span className="tm-mini-stat">
      <span className="tm-mini-label">{label}</span>
      <span className="tm-mini-value">{value}</span>
    </span>
  );
}
