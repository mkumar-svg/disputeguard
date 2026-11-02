package dev.disputeguard.evidence;

/** What DisputeGuard suggests the merchant does. */
public enum Recommendation {
    /** Strong evidence: submit it with provide-evidence. */
    FIGHT,
    /** Mixed evidence: settle early with a partial refund (make-offer). */
    OFFER_PARTIAL_REFUND,
    /** Weak evidence: accept the claim and refund (accept-claim). */
    ACCEPT;

    static final double FIGHT_FROM = 0.65;
    static final double OFFER_FROM = 0.40;

    public static Recommendation forProbability(double winProbability) {
        if (winProbability >= FIGHT_FROM) {
            return FIGHT;
        }
        return winProbability >= OFFER_FROM ? OFFER_PARTIAL_REFUND : ACCEPT;
    }
}
