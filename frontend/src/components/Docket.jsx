import { REASONS, RECOMMENDATIONS, STATUSES, daysLeft, money, pct } from '../labels.js';

export default function Docket({ rows, loading, selected, onSelect }) {
  return (
    <aside className="docket" aria-label="Disputes">
      <h2 className="docket-title">
        Disputes <span className="count">{rows.length}</span>
      </h2>
      {loading && <p className="muted pad">Loading…</p>}
      <ul>
        {rows.map(({ dispute: d, recommendation, winProbability, packStatus }) => {
          const status = STATUSES[d.status] || { text: d.status, tone: 'waiting' };
          const rec = RECOMMENDATIONS[recommendation];
          const left = d.status === 'WAITING_FOR_SELLER_RESPONSE' ? daysLeft(d.sellerResponseDue) : null;
          return (
            <li key={d.paypalDisputeId}>
              <button
                className={selected === d.paypalDisputeId ? 'docket-item active' : 'docket-item'}
                onClick={() => onSelect(d.paypalDisputeId)}
                aria-current={selected === d.paypalDisputeId}
              >
                <span className="docket-row">
                  <span className="docket-reason">{REASONS[d.reason] || d.reason}</span>
                  <span className="docket-amount">{money(d.amount, d.currency)}</span>
                </span>
                <span className="docket-id">{d.paypalDisputeId}</span>
                <span className="docket-row">
                  <span className={`dot ${status.tone}`}>{status.text}</span>
                  {left != null && <span className="docket-due">{left > 0 ? `${left} days left` : 'Due today'}</span>}
                </span>
                {rec && (
                  <span className={`docket-rec ${rec.tone}`}>
                    {packStatus === 'SUBMITTED' ? 'Answered' : rec.text} · {pct(winProbability)} win chance
                  </span>
                )}
              </button>
            </li>
          );
        })}
      </ul>
    </aside>
  );
}
