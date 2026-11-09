import { useState } from 'react';
import { api } from '../api.js';
import { ACTIONS, DONE_ACTIONS, RECOMMENDATIONS, day, money } from '../labels.js';
import { ErrorBox } from './CaseFile.jsx';

const ORDER = ['FIGHT', 'OFFER_PARTIAL_REFUND', 'ACCEPT'];

export default function Response({ disputeId, currency, pack, availableActions, onDone }) {
  const allowed = ORDER.filter((a) => availableActions?.includes(a));
  const initial = allowed.includes(pack?.recommendation) ? pack.recommendation : allowed[0];
  const [action, setAction] = useState(initial);
  const [note, setNote] = useState(pack?.draftNote || '');
  const [offer, setOffer] = useState(pack?.suggestedRefund ?? '');
  const [confirming, setConfirming] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);

  if (!pack) return null;

  if (pack.status === 'SUBMITTED') {
    return (
      <section className="panel response done">
        <h2>{DONE_ACTIONS[pack.submitAction] || 'Response sent'}</h2>
        <p className="muted">Sent on {day(pack.submittedAt)}. PayPal now decides; the status above updates as it does.</p>
        <div className="sent-note">{pack.draftNote}</div>
      </section>
    );
  }

  if (allowed.length === 0) {
    return (
      <section className="panel response">
        <h2>Your response</h2>
        <p className="muted">PayPal is not accepting a response on this dispute right now.</p>
      </section>
    );
  }

  async function send() {
    setSending(true);
    setError(null);
    try {
      await api.approve(disputeId, {
        action,
        note,
        offerAmount: action === 'OFFER_PARTIAL_REFUND' && offer !== '' ? Number(offer) : null,
        approvedBy: 'merchant',
      });
      setConfirming(false);
      await onDone();
    } catch (e) {
      setError(e);
      setConfirming(false);
    } finally {
      setSending(false);
    }
  }

  const changed = note !== (pack.draftNote || '');

  return (
    <section className="panel response" aria-label="Your response">
      <h2>Your response</h2>
      <fieldset className="choices">
        <legend className="sr-only">What to do</legend>
        {allowed.map((a) => (
          <label key={a} className={action === a ? 'choice active' : 'choice'}>
            <input type="radio" name="action" value={a} checked={action === a} onChange={() => { setAction(a); setConfirming(false); }} />
            <span className="choice-name">{ACTIONS[a]}</span>
            {pack.recommendation === a && <span className="choice-tag">Recommended</span>}
          </label>
        ))}
      </fieldset>

      {action === 'OFFER_PARTIAL_REFUND' && (
        <label className="field offer-field">
          <span>Refund to offer ({currency})</span>
          <input type="number" min="1" step="0.01" value={offer} onChange={(e) => setOffer(e.target.value)} />
        </label>
      )}

      <label className="field">
        <span>Note to PayPal {changed && <em className="edited">edited by you</em>}</span>
        <textarea rows={6} maxLength={2000} value={note} onChange={(e) => setNote(e.target.value)} />
        <span className="field-hint">{note.length} / 2000 characters. Drafted by DisputeGuard from the order data; change anything you like.</span>
      </label>

      {error && <ErrorBox error={error} />}

      {!confirming ? (
        <button className={`btn big ${RECOMMENDATIONS[action]?.tone || ''}`} onClick={() => setConfirming(true)} disabled={!note.trim()}>
          {ACTIONS[action]}
        </button>
      ) : (
        <div className="confirm">
          <p>
            {action === 'FIGHT' && 'PayPal will receive the tracking details and your note. This cannot be undone.'}
            {action === 'OFFER_PARTIAL_REFUND' && `PayPal will offer the buyer ${money(offer, currency)}. If they accept, the dispute closes.`}
            {action === 'ACCEPT' && 'PayPal will close the dispute in the buyer’s favor and refund them. This cannot be undone.'}
          </p>
          <div className="confirm-buttons">
            <button className={`btn ${RECOMMENDATIONS[action]?.tone || ''}`} onClick={send} disabled={sending}>
              {sending ? 'Sending to PayPal…' : `Yes, ${ACTIONS[action].toLowerCase()}`}
            </button>
            <button className="btn quiet" onClick={() => setConfirming(false)} disabled={sending}>
              Not yet
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
