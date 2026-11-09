import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { CHECK, REASONS, STATUSES, TOOLS, day, daysLeft, money } from '../labels.js';
import WinMeter from './WinMeter.jsx';
import Response from './Response.jsx';
import OrderTrail from './OrderTrail.jsx';

export default function CaseFile({ disputeId, onChanged }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [working, setWorking] = useState(null); // 'analyze' | 'preview'
  const [preview, setPreview] = useState(null);

  async function load() {
    setError(null);
    try {
      setData(await api.getDispute(disputeId));
    } catch (e) {
      setError(e);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [disputeId]);

  async function analyze(isPreview) {
    setWorking(isPreview ? 'preview' : 'analyze');
    setError(null);
    try {
      const res = await api.analyze(disputeId, isPreview);
      if (isPreview) {
        setPreview(res.evidencePack);
      } else {
        setPreview(null);
        setData(res);
        onChanged();
      }
    } catch (e) {
      setError(e);
    } finally {
      setWorking(null);
    }
  }

  if (!data && !error) return <section className="case"><p className="muted pad">Opening case…</p></section>;
  if (!data) return <section className="case"><ErrorBox error={error} /></section>;

  const { dispute: d, order, availableActions } = data;
  const pack = preview || data.evidencePack;
  const status = STATUSES[d.status] || { text: d.status, tone: 'waiting' };
  const left = daysLeft(d.sellerResponseDue);
  const answered = data.evidencePack?.status === 'SUBMITTED';

  return (
    <section className="case" aria-label={`Dispute ${d.paypalDisputeId}`}>
      <header className="case-head">
        <div className="case-title">
          <h1>{REASONS[d.reason] || d.reason}</h1>
          <span className="case-amount">{money(d.amount, d.currency)}</span>
        </div>
        <dl className="case-facts">
          <div>
            <dt>Dispute</dt>
            <dd className="code">{d.paypalDisputeId}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd><span className={`dot ${status.tone}`}>{status.text}</span></dd>
          </div>
          <div>
            <dt>Opened</dt>
            <dd>{day(d.openedAt) || '—'}</dd>
          </div>
          {d.status === 'WAITING_FOR_SELLER_RESPONSE' && left != null && (
            <div>
              <dt>Respond by</dt>
              <dd className={left <= 3 ? 'due-soon' : ''}>
                {day(d.sellerResponseDue)} ({left > 0 ? `${left} days` : 'today'})
              </dd>
            </div>
          )}
        </dl>
        {d.buyerMessage && <blockquote className="buyer-quote">“{d.buyerMessage}” <cite>the buyer</cite></blockquote>}
      </header>

      {error && <ErrorBox error={error} />}

      {preview && (
        <div className="preview-banner" role="status">
          <span>Test run of the agent. Nothing was saved and nothing was sent to PayPal.</span>
          <button className="btn quiet small" onClick={() => setPreview(null)}>Back to the saved pack</button>
        </div>
      )}

      {!pack ? (
        <div className="no-pack">
          <h2>Evidence not prepared yet</h2>
          <p>DisputeGuard will read the order, the tracking and the buyer's messages, then score the case.</p>
          <button className="btn" onClick={() => analyze(false)} disabled={!!working}>
            {working === 'analyze' ? 'Preparing evidence…' : 'Prepare evidence pack'}
          </button>
        </div>
      ) : (
        <>
          <WinMeter
            base={pack.baseProbability}
            final={pack.winProbability}
            recommendation={pack.recommendation}
            adjustmentReason={pack.adjustmentReason}
          />

          {pack.reasonSummary && <p className="claim-summary">{pack.reasonSummary}</p>}

          <div className="case-grid">
            <section className="panel">
              <h2>Evidence PayPal looks for</h2>
              <ul className="ledger">
                {(pack.checklist || []).map((item) => {
                  const c = CHECK[item.status] || { text: item.status, tone: 'partial' };
                  return (
                    <li key={item.key} className="ledger-row">
                      <span className={`stamp ${c.tone}`}>{c.text}</span>
                      <div>
                        <div className="ledger-label">{item.label}</div>
                        <div className="ledger-detail">{item.detail}</div>
                      </div>
                    </li>
                  );
                })}
              </ul>
            </section>

            <section className="panel">
              <h2>How the agent built this</h2>
              <ol className="steps">
                {(pack.agentSteps || []).map((s, i) => (
                  <li key={i}>
                    <span className="step-tool">{TOOLS[s.tool] || s.tool}</span>
                    <span className="step-summary">{s.summary}</span>
                  </li>
                ))}
              </ol>
              <p className="model-line">
                {pack.model === 'rules' ? 'No AI model was used for this pack (rules only).' : <>Model: <span className="code">{pack.model}</span></>}
              </p>
              {pack.guardrailNotes?.length > 0 ? (
                <div className="guardrails">
                  <h3>Checks that need your eye</h3>
                  <ul>
                    {pack.guardrailNotes.map((g, i) => <li key={i}>{g}</li>)}
                  </ul>
                </div>
              ) : (
                pack.model !== 'rules' && <p className="guardrails-ok">Every fact in the note matches the order data.</p>
              )}
              {!answered && (
                <div className="agent-actions">
                  <button className="btn quiet small" onClick={() => analyze(false)} disabled={!!working}>
                    {working === 'analyze' ? 'Running…' : 'Run the analysis again'}
                  </button>
                </div>
              )}
              {answered && (
                <div className="agent-actions">
                  <button className="btn quiet small" onClick={() => analyze(true)} disabled={!!working}>
                    {working === 'preview' ? 'Running…' : 'Test the agent again (nothing is saved)'}
                  </button>
                </div>
              )}
            </section>
          </div>

          <OrderTrail order={order} />

          {!preview && (
            <Response
              key={`${data.evidencePack?.id}-${data.evidencePack?.updatedAt}-${data.evidencePack?.status}`}
              disputeId={d.paypalDisputeId}
              currency={d.currency}
              pack={data.evidencePack}
              availableActions={availableActions}
              onDone={async () => {
                await load();
                onChanged();
              }}
            />
          )}
        </>
      )}
    </section>
  );
}

export function ErrorBox({ error }) {
  return (
    <div className="error-box" role="alert">
      <strong>{error.message}</strong>
      {error.details && <pre>{typeof error.details === 'string' ? error.details : JSON.stringify(error.details, null, 2)}</pre>}
    </div>
  );
}
