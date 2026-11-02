package dev.disputeguard.evidence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** The evidence pack DisputeGuard prepared for a dispute, and what happened when it was submitted. */
@Entity
@Table(name = "evidence_packs")
public class EvidencePack {

    /** READY (waiting for the merchant), SUBMITTED, SUBMIT_FAILED. */
    public static final String READY = "READY";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String SUBMIT_FAILED = "SUBMIT_FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispute_id", nullable = false)
    private Long disputeId;

    private String status;
    private String recommendation;

    @Column(name = "win_probability")
    private BigDecimal winProbability;

    @Column(name = "base_probability")
    private BigDecimal baseProbability;

    @Column(name = "adjustment_reason")
    private String adjustmentReason;

    @Column(name = "reason_summary")
    private String reasonSummary;

    /** JSON array of {@link ChecklistItem}. */
    private String checklist;

    @Column(name = "draft_note")
    private String draftNote;

    @Column(name = "suggested_refund")
    private BigDecimal suggestedRefund;

    /** JSON array of guardrail findings. */
    @Column(name = "guardrail_notes")
    private String guardrailNotes;

    /** JSON array of the agent's tool calls. */
    @Column(name = "agent_trace")
    private String agentTrace;

    private String model;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "submit_action")
    private String submitAction;

    @Column(name = "submit_response")
    private String submitResponse;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    private String outcome;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected EvidencePack() {
    }

    public EvidencePack(Long disputeId) {
        this.disputeId = disputeId;
        this.status = READY;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getDisputeId() { return disputeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public BigDecimal getWinProbability() { return winProbability; }
    public void setWinProbability(BigDecimal winProbability) { this.winProbability = winProbability; }
    public BigDecimal getBaseProbability() { return baseProbability; }
    public void setBaseProbability(BigDecimal baseProbability) { this.baseProbability = baseProbability; }
    public String getAdjustmentReason() { return adjustmentReason; }
    public void setAdjustmentReason(String adjustmentReason) { this.adjustmentReason = adjustmentReason; }
    public String getReasonSummary() { return reasonSummary; }
    public void setReasonSummary(String reasonSummary) { this.reasonSummary = reasonSummary; }
    public String getChecklist() { return checklist; }
    public void setChecklist(String checklist) { this.checklist = checklist; }
    public String getDraftNote() { return draftNote; }
    public void setDraftNote(String draftNote) { this.draftNote = draftNote; }
    public BigDecimal getSuggestedRefund() { return suggestedRefund; }
    public void setSuggestedRefund(BigDecimal suggestedRefund) { this.suggestedRefund = suggestedRefund; }
    public String getGuardrailNotes() { return guardrailNotes; }
    public void setGuardrailNotes(String guardrailNotes) { this.guardrailNotes = guardrailNotes; }
    public String getAgentTrace() { return agentTrace; }
    public void setAgentTrace(String agentTrace) { this.agentTrace = agentTrace; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getSubmitAction() { return submitAction; }
    public void setSubmitAction(String submitAction) { this.submitAction = submitAction; }
    public String getSubmitResponse() { return submitResponse; }
    public void setSubmitResponse(String submitResponse) { this.submitResponse = submitResponse; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
