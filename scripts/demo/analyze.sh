#!/usr/bin/env bash
# Runs the AI agent on a dispute and prints the evidence pack.
# Usage: ./analyze.sh <dispute_id>
source "$(dirname "$0")/_api.sh"
id="${1:?Usage: $0 <dispute_id>}"
curl -sS -X POST "$API/api/disputes/$id/analyze" |
  jq '{dispute: .dispute.paypalDisputeId, reason: .dispute.reason, status: .dispute.status,
       linkedOrder: .order.paypalOrderId,
       recommendation: .evidencePack.recommendation, winProbability: .evidencePack.winProbability,
       baseProbability: .evidencePack.baseProbability, adjustment: .evidencePack.adjustmentReason,
       summary: .evidencePack.reasonSummary,
       checklist: [.evidencePack.checklist[]? | "\(.status)  \(.label): \(.detail)"],
       agentSteps: [.evidencePack.agentSteps[]? | "\(.tool): \(.summary)"],
       guardrails: .evidencePack.guardrailNotes, model: .evidencePack.model,
       note: .evidencePack.draftNote, availableActions, error, details}'
