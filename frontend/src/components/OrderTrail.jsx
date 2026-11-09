import { dayTime, money } from '../labels.js';

// The order, its tracking scans and the buyer-seller messages, merged into one timeline,
// because a dispute is argued over what happened when.
export default function OrderTrail({ order }) {
  if (!order) {
    return (
      <section className="panel trail">
        <h2>Order history</h2>
        <p className="muted">
          No store order is linked to this dispute, so there is no shipping or chat evidence. Import the order from
          the demo store to link it.
        </p>
      </section>
    );
  }

  const events = [];
  if (order.capturedAt) {
    events.push({ at: order.capturedAt, kind: 'paid', title: `Paid ${money(order.amount, order.currency)}`, text: order.description });
  }
  (order.shipment?.events || []).forEach((e) =>
    events.push({ at: e.time, kind: e.status === 'DELIVERED' ? 'delivered' : 'scan', title: e.description, text: e.location })
  );
  (order.messages || []).forEach((m) =>
    events.push({ at: m.sentAt, kind: m.sender === 'SELLER' ? 'seller' : 'buyer', title: m.sender === 'SELLER' ? 'You wrote' : 'Buyer wrote', text: m.text })
  );
  events.sort((a, b) => new Date(a.at) - new Date(b.at));

  return (
    <section className="panel trail">
      <div className="trail-head">
        <h2>Order history</h2>
        <dl className="trail-facts">
          <div><dt>Order</dt><dd className="code">{order.paypalOrderId}</dd></div>
          {order.shipment && (
            <div><dt>{order.shipment.carrier}</dt><dd className="code">{order.shipment.trackingNo}</dd></div>
          )}
          {order.shippingAddress && <div><dt>Ship to</dt><dd>{order.shippingAddress}</dd></div>}
        </dl>
      </div>
      <ol className="timeline">
        {events.map((e, i) => (
          <li key={i} className={`tl ${e.kind}`}>
            <time>{dayTime(e.at)}</time>
            <div className="tl-body">
              <div className="tl-title">{e.title}</div>
              {e.text && <div className={e.kind === 'buyer' || e.kind === 'seller' ? 'tl-text said' : 'tl-text'}>{e.text}</div>}
            </div>
          </li>
        ))}
      </ol>
      {order.scenario && (
        <p className="fine-print">Demo store: shipping and messages are simulated for the “{order.scenario.toLowerCase().replace('_', ' ')}” story; the payment is a real sandbox payment.</p>
      )}
    </section>
  );
}
