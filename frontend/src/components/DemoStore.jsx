import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { SCENARIOS, day, money } from '../labels.js';
import { ErrorBox } from './CaseFile.jsx';

export default function DemoStore() {
  const [orders, setOrders] = useState([]);
  const [form, setForm] = useState({ amount: '49.99', description: 'Wireless earbuds', scenario: 'DELIVERED' });
  const [created, setCreated] = useState(null);
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState(null);

  async function load() {
    try {
      setOrders(await api.listOrders());
    } catch (e) {
      setError(e);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function create(e) {
    e.preventDefault();
    setBusy('create');
    setError(null);
    try {
      const res = await api.createOrder({ ...form, amount: Number(form.amount) });
      setCreated(res);
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  async function capture(id) {
    setBusy(id);
    setError(null);
    try {
      await api.captureOrder(id);
      if (created?.paypalOrderId === id) setCreated(null);
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  return (
    <main className="store">
      <section className="panel store-form">
        <h1>Place a test order</h1>
        <p className="muted">
          A real PayPal sandbox payment. The store does not ship anything, so after payment it records a simulated
          shipment and buyer messages for the story you pick. That story decides how strong your evidence is if the
          buyer disputes.
        </p>
        <form onSubmit={create}>
          <label className="field">
            <span>Item</span>
            <input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} required />
          </label>
          <label className="field">
            <span>Price (USD)</span>
            <input type="number" min="1" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} required />
          </label>
          <label className="field">
            <span>What happens after payment</span>
            <select value={form.scenario} onChange={(e) => setForm({ ...form, scenario: e.target.value })}>
              {Object.entries(SCENARIOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <button className="btn" disabled={busy === 'create'}>{busy === 'create' ? 'Creating…' : 'Create order'}</button>
        </form>

        {created && (
          <ol className="next-steps">
            <li>
              <a href={created.approveLink} target="_blank" rel="noreferrer">Open PayPal checkout</a> in a private window and pay
              as your US sandbox buyer. You will land on example.com when it is done.
            </li>
            <li>
              Come back and{' '}
              <button className="btn small" onClick={() => capture(created.paypalOrderId)} disabled={busy === created.paypalOrderId}>
                {busy === created.paypalOrderId ? 'Capturing…' : 'Capture the payment'}
              </button>
            </li>
            <li>On sandbox.paypal.com, as the buyer, open the payment and report a problem. It reaches the Disputes tab within a few minutes.</li>
          </ol>
        )}
        {error && <ErrorBox error={error} />}
      </section>

      <section className="panel store-orders">
        <h2>Orders</h2>
        {orders.length === 0 ? (
          <p className="muted">No orders yet.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Item</th><th>Amount</th><th>Story</th><th>Payment</th><th>Placed</th></tr>
              </thead>
              <tbody>
                {orders.map((o) => (
                  <tr key={o.id}>
                    <td>{o.description}<div className="code small">{o.paypalOrderId}</div></td>
                    <td>{money(o.amount, o.currency)}</td>
                    <td>{(SCENARIOS[o.scenario] || o.scenario || '').split(' (')[0]}</td>
                    <td>
                      {o.captureId ? 'Captured' : (
                        <button className="btn small quiet" onClick={() => capture(o.paypalOrderId)} disabled={busy === o.paypalOrderId}>
                          {busy === o.paypalOrderId ? 'Capturing…' : 'Capture'}
                        </button>
                      )}
                    </td>
                    <td>{day(o.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  );
}
