import { useState, useEffect } from 'react';
import { getFinances } from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineBanknotes,
  HiOutlineArrowTrendingUp,
  HiOutlineArrowTrendingDown,
  HiOutlineUserGroup,
  HiOutlineFunnel,
} from 'react-icons/hi2';
import './Finances.css';

const TX_TYPES = [
  { value: '', label: 'All' },
  { value: 'TM_LISTING_FEE', label: 'Listing Fees' },
  { value: 'TM_SALE', label: 'Sales' },
  { value: 'TM_PURCHASE', label: 'Purchases' },
  { value: 'TM_TAX_SETTLE', label: 'Tax Settlement' },
  { value: 'SALARY', label: 'Salaries' },
  { value: 'MATCH_INCOME', label: 'Match Income' },
  { value: 'SEAT_INCOME', label: 'Seat Income' },
];

const TYPE_COLORS = {
  TM_LISTING_FEE: '#f59e0b',
  TM_SALE: '#22c55e',
  TM_PURCHASE: '#ef4444',
  TM_TAX_SETTLE: '#a855f7',
  SALARY: '#ef4444',
  MATCH_INCOME: '#22c55e',
  SEAT_INCOME: '#3b82f6',
};

function formatMoney(val) {
  if (val == null) return '$0';
  const abs = Math.abs(val);
  const formatted = abs >= 1000000
    ? `$${(abs / 1000000).toFixed(1)}M`
    : `$${abs.toLocaleString()}`;
  return val < 0 ? `-${formatted}` : formatted;
}

function formatDate(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
    + ' ' + d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
}

function typeLabel(type) {
  return type.replace(/_/g, ' ').replace(/\bTM\b/, 'TM');
}

export default function Finances() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('');

  const load = (type) => {
    setLoading(true);
    getFinances(type || undefined)
      .then((res) => setData(res.data))
      .catch(() => toast.error('Failed to load finances'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(filter); }, [filter]);

  if (loading && !data) {
    return <div className="fin-page"><div className="fin-loading">Loading finances...</div></div>;
  }
  if (!data) return null;

  return (
    <div className="fin-page">
      <div className="fin-header">
        <HiOutlineBanknotes className="fin-header-icon" />
        <h1 className="fin-title">Finances</h1>
      </div>

      {/* Summary Cards */}
      <div className="fin-cards">
        <div className="fin-card fin-card-balance">
          <div className="fin-card-icon-wrap balance">
            <HiOutlineBanknotes />
          </div>
          <div className="fin-card-body">
            <span className="fin-card-label">Balance</span>
            <span className="fin-card-value balance">{formatMoney(data.balance)}</span>
          </div>
        </div>

        <div className="fin-card">
          <div className="fin-card-icon-wrap income">
            <HiOutlineArrowTrendingUp />
          </div>
          <div className="fin-card-body">
            <span className="fin-card-label">Total Income</span>
            <span className="fin-card-value income">{formatMoney(data.totalIncome)}</span>
          </div>
        </div>

        <div className="fin-card">
          <div className="fin-card-icon-wrap expense">
            <HiOutlineArrowTrendingDown />
          </div>
          <div className="fin-card-body">
            <span className="fin-card-label">Total Expenses</span>
            <span className="fin-card-value expense">{formatMoney(data.totalExpenses)}</span>
          </div>
        </div>

        <div className="fin-card">
          <div className="fin-card-icon-wrap squad">
            <HiOutlineUserGroup />
          </div>
          <div className="fin-card-body">
            <span className="fin-card-label">Squad Wages</span>
            <span className="fin-card-value">
              {formatMoney(data.totalWages)}<span className="fin-wage-period">/day</span>
            </span>
            <span className="fin-card-sub">{data.squadSize} players</span>
          </div>
        </div>
      </div>

      {/* Transaction History */}
      <div className="fin-section">
        <div className="fin-section-header">
          <h2 className="fin-section-title">Transaction History</h2>
          <div className="fin-filter">
            <HiOutlineFunnel className="fin-filter-icon" />
            <select
              className="fin-filter-select"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            >
              {TX_TYPES.map((t) => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>
          </div>
        </div>

        {data.transactions.length === 0 ? (
          <div className="fin-empty">No transactions found</div>
        ) : (
          <div className="fin-tx-list">
            {data.transactions.map((tx) => (
              <div key={tx.id} className="fin-tx-row">
                <div
                  className="fin-tx-indicator"
                  style={{ background: TYPE_COLORS[tx.type] || '#64748b' }}
                />
                <div className="fin-tx-info">
                  <span className="fin-tx-desc">{tx.description}</span>
                  <div className="fin-tx-meta">
                    <span
                      className="fin-tx-type"
                      style={{ color: TYPE_COLORS[tx.type] || '#64748b' }}
                    >
                      {typeLabel(tx.type)}
                    </span>
                    <span className="fin-tx-date">{formatDate(tx.createdAt)}</span>
                  </div>
                </div>
                <div className="fin-tx-amounts">
                  <span className={`fin-tx-amount ${tx.amount >= 0 ? 'income' : 'expense'}`}>
                    {tx.amount >= 0 ? '+' : ''}{formatMoney(tx.amount)}
                  </span>
                  <span className="fin-tx-balance">Bal: {formatMoney(tx.balanceAfter)}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
