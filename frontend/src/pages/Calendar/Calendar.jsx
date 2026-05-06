import { useEffect, useState } from 'react';
import { getSeasonCalendar } from '../../api/auth';
import toast from 'react-hot-toast';
import { HiOutlineCalendarDays, HiOutlineSparkles } from 'react-icons/hi2';
import './Calendar.css';

export default function Calendar() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    getSeasonCalendar()
      .then((res) => setData(res.data))
      .catch(() => toast.error('Failed to load season calendar'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="cal-loading">Loading season calendar...</div>;
  if (!data) return <div className="cal-loading">Season calendar unavailable.</div>;

  const weekday = (w) => w?.slice(0, 1) + w?.slice(1).toLowerCase();

  return (
    <div className="cal-page">
      <section className="cal-header">
        <div className="cal-header-title">
          <HiOutlineCalendarDays className="cal-icon" />
          <div>
            <h1>Season Calendar</h1>
            <p>Season {data.season} · Day {data.currentSeasonDay}/{data.totalSeasonDays}</p>
          </div>
        </div>
        <div className="cal-header-meta">
          <span>Start: {data.seasonStartDate}</span>
          <span>End: {data.seasonEndDate}</span>
        </div>
      </section>

      <section className="cal-grid">
        {data.days.map((d) => (
          <article key={d.seasonDay} className={`cal-day ${d.isToday ? 'today' : ''}`}>
            <div className="cal-day-top">
              <span className="cal-day-num">Day {d.seasonDay}</span>
              {d.isToday && (
                <span className="cal-today-pill">
                  <HiOutlineSparkles /> Today
                </span>
              )}
            </div>
            <div className="cal-day-date">{d.date} · {weekday(d.weekday)}</div>
            <div className="cal-events">
              {d.events.map((e, i) => (
                <span key={i} className="cal-event">{e}</span>
              ))}
            </div>
          </article>
        ))}
      </section>
    </div>
  );
}

