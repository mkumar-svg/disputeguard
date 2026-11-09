import { RECOMMENDATIONS, pct } from '../labels.js';

// The win chance on a scale split into the three decisions, with the rules-only score as a hollow marker
// and the final score (after the agent's adjustment) as the solid one.
export default function WinMeter({ base, final, recommendation, adjustmentReason }) {
  const rec = RECOMMENDATIONS[recommendation];
  const f = Math.round(Number(final) * 100);
  const b = Math.round(Number(base) * 100);
  const moved = f !== b;

  return (
    <section className="meter" aria-label="Win chance">
      <div className="meter-head">
        <div>
          <div className={`meter-number ${rec?.tone || ''}`}>{pct(final)}</div>
          <div className="meter-caption">chance of winning this dispute</div>
        </div>
        {rec && (
          <div className="meter-verdict">
            <div className={`verdict ${rec.tone}`}>{rec.text}</div>
            <p className="meter-why">{rec.why}</p>
          </div>
        )}
      </div>

      <div className="scale" role="img" aria-label={`Win chance ${f} percent${moved ? `, rules alone gave ${b} percent` : ''}`}>
        <div className="zone accept" style={{ width: '40%' }}>
          <span>Accept</span>
        </div>
        <div className="zone offer" style={{ width: '25%' }}>
          <span>Offer refund</span>
        </div>
        <div className="zone fight" style={{ width: '35%' }}>
          <span>Fight</span>
        </div>
        {moved && <div className="marker base" style={{ left: `${b}%` }} title={`Rules alone: ${b}%`} />}
        <div className={`marker final ${rec?.tone || ''}`} style={{ left: `${f}%` }} />
      </div>

      <p className="meter-note">
        {moved ? (
          <>
            Evidence checklist alone: <strong>{b}%</strong>. The agent moved it to <strong>{f}%</strong>:{' '}
            {adjustmentReason}
          </>
        ) : (
          <>Score comes from the evidence checklist below. {adjustmentReason && adjustmentReason !== 'No adjustment' ? adjustmentReason : ''}</>
        )}
      </p>
    </section>
  );
}
