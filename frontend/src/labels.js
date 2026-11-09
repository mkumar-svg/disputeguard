// Plain-language names for PayPal's codes, as a merchant would say them.

export const REASONS = {
  MERCHANDISE_OR_SERVICE_NOT_RECEIVED: 'Item not received',
  MERCHANDISE_OR_SERVICE_NOT_AS_DESCRIBED: 'Not as described',
  UNAUTHORISED: 'Unauthorized payment',
  CREDIT_NOT_PROCESSED: 'Refund not received',
  DUPLICATE_TRANSACTION: 'Charged twice',
  INCORRECT_AMOUNT: 'Wrong amount charged',
  PAYMENT_BY_OTHER_MEANS: 'Paid another way',
  CANCELED_RECURRING_BILLING: 'Subscription canceled',
  PROBLEM_WITH_REMITTANCE: 'Payment problem',
  OTHER: 'Other',
};

export const STATUSES = {
  WAITING_FOR_SELLER_RESPONSE: { text: 'Your response needed', tone: 'urgent' },
  WAITING_FOR_BUYER_RESPONSE: { text: 'Waiting for the buyer', tone: 'waiting' },
  UNDER_REVIEW: { text: 'PayPal is reviewing', tone: 'waiting' },
  OPEN: { text: 'Open', tone: 'urgent' },
  RESOLVED: { text: 'Resolved', tone: 'done' },
  OTHER: { text: 'Other', tone: 'waiting' },
};

export const RECOMMENDATIONS = {
  FIGHT: { text: 'Fight it', tone: 'fight', why: 'Your evidence is strong. Send it to PayPal.' },
  OFFER_PARTIAL_REFUND: {
    text: 'Offer a partial refund',
    tone: 'offer',
    why: 'The evidence is mixed. Settling early usually costs less than losing.',
  },
  ACCEPT: {
    text: 'Accept the claim',
    tone: 'accept',
    why: 'The evidence is weak. Fighting would likely lose and take time.',
  },
};

export const ACTIONS = {
  FIGHT: 'Send evidence to PayPal',
  OFFER_PARTIAL_REFUND: 'Offer a partial refund',
  ACCEPT: 'Accept and refund the buyer',
};

export const DONE_ACTIONS = {
  FIGHT: 'Evidence sent to PayPal',
  OFFER_PARTIAL_REFUND: 'Partial refund offered',
  ACCEPT: 'Claim accepted, buyer refunded',
};

export const CHECK = {
  PRESENT: { text: 'Have it', tone: 'have' },
  WEAK: { text: 'Partial', tone: 'partial' },
  MISSING: { text: 'Missing', tone: 'missing' },
};

export const TOOLS = {
  get_order: 'Read the order',
  get_tracking: 'Checked tracking',
  get_chat_logs: 'Read buyer messages',
  rules: 'Rules only',
};

export const SCENARIOS = {
  DELIVERED: 'Delivered with tracking (strong case)',
  IN_TRANSIT: 'Still in transit (mixed case)',
  NO_TRACKING: 'No tracking, buyer ignored (weak case)',
};

export function money(amount, currency = 'USD') {
  if (amount == null) return '';
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount));
  } catch {
    return `${amount} ${currency}`;
  }
}

export function day(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function dayTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('en-US', {
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export function daysLeft(iso) {
  if (!iso) return null;
  return Math.ceil((new Date(iso).getTime() - Date.now()) / 86400000);
}

export function pct(p) {
  return p == null ? '' : `${Math.round(Number(p) * 100)}%`;
}
