#!/usr/bin/env bash
# Runs the AI agent on a dispute and prints the evidence pack.
# Usage: ./analyze.sh <dispute_id> [--preview]
#   --preview  dry run: nothing is saved, and it works even on a dispute that was already answered
source "$(dirname "$0")/_api.sh"
id="${1:?Usage: $0 <dispute_id> [--preview]}"
query=""
[[ "${2:-}" == "--preview" ]] && query="?preview=true"
curl -sS -X POST "$API/api/disputes/$id/analyze$query" |
  jq '{dispute: .dispute.paypalDisputeId, reason: .dispute.reason, status: .dispute.status,
       linkedOrder: .order.paypalOrderId,
       packStatus: .evidencePack.status, submittedAction: .evidencePack.submitAction,
       recommendation: .evidencePack.recommendation, winProbability: .evidencePack.winProbability,
       baseProbability: .evidencePack.baseProbability, adjustment: .evidencePack.adjustmentReason,
       summary: .evidencePack.reasonSummary,
       checklist: [.evidencePack.checklist[]? | "\(.status)  \(.label): \(.detail)"],
       agentSteps: [.evidencePack.agentSteps[]? | "\(.tool): \(.summary)"],
       guardrails: .evidencePack.guardrailNotes, model: .evidencePack.model,
       note: .evidencePack.draftNote, availableActions, error, details}'
