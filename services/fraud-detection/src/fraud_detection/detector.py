"""Rules-based fraud risk scoring engine.

Each rule contributes points to an overall risk score; the score maps to an
APPROVE / REVIEW / DECLINE decision. Examiners care that every decline path is
deterministic and explainable, so each rule records a human-readable reason.
"""

from __future__ import annotations

from typing import Dict, Iterable, List, Set

from .models import RiskAssessment, RiskDecision, Transaction


class FraudDetector:
    """Score transactions against a configurable set of fraud heuristics."""

    def __init__(
        self,
        high_amount_threshold: float = 10_000.0,
        home_country: str = "US",
        review_threshold: int = 40,
        decline_threshold: int = 70,
        trusted_devices: Iterable[str] | None = None,
    ) -> None:
        self.high_amount_threshold = high_amount_threshold
        self.home_country = home_country
        self.review_threshold = review_threshold
        self.decline_threshold = decline_threshold
        self.trusted_devices: Set[str] = set(trusted_devices or [])

    def decide(self, score: int) -> RiskDecision:
        """Map a numeric risk score to a decision."""
        if score >= self.decline_threshold:
            return RiskDecision.DECLINE
        if score >= self.review_threshold:
            return RiskDecision.REVIEW
        return RiskDecision.APPROVE

    def _amount_rule(self, txn: Transaction, reasons: List[str]) -> int:
        if txn.amount <= 0:
            raise ValueError("transaction amount must be positive")
        if txn.amount >= self.high_amount_threshold:
            reasons.append(
                f"amount {txn.amount} >= high-amount threshold {self.high_amount_threshold}"
            )
            return 45
        return 0

    def _geo_rule(self, txn: Transaction, reasons: List[str]) -> int:
        if txn.country != self.home_country:
            reasons.append(f"out-of-country transaction in {txn.country}")
            return 30
        return 0

    def _velocity_rule(
        self, txn: Transaction, recent_count: int, reasons: List[str]
    ) -> int:
        if recent_count >= 5:
            reasons.append(f"velocity spike: {recent_count} recent transactions")
            return 35
        return 0

    def _device_rule(self, txn: Transaction, reasons: List[str]) -> int:
        if self.trusted_devices and txn.device_id not in self.trusted_devices:
            reasons.append(f"unrecognized device {txn.device_id}")
            return 20
        return 0

    def score(self, txn: Transaction, recent_counts: Dict[str, int] | None = None) -> RiskAssessment:
        """Score a transaction, returning the risk assessment with reasons."""
        if txn is None:
            raise ValueError("transaction must not be None")
        reasons: List[str] = []
        recent = (recent_counts or {}).get(txn.customer_id, 0)

        total = 0
        total += self._amount_rule(txn, reasons)
        total += self._geo_rule(txn, reasons)
        total += self._velocity_rule(txn, recent, reasons)
        total += self._device_rule(txn, reasons)
        total = min(total, 100)

        return RiskAssessment(
            score=total,
            decision=self.decide(total),
            reasons=reasons,
            transaction=txn,
        )

    def score_batch(
        self,
        transactions: Iterable[Transaction],
        recent_counts: Dict[str, int] | None = None,
    ) -> List[RiskAssessment]:
        """Score a batch of transactions, e.g. an end-of-day settlement file."""
        results: List[RiskAssessment] = []
        for txn in transactions:
            results.append(self.score(txn, recent_counts))
        return results

    def explain(self, assessment: RiskAssessment) -> str:
        """Render a human-readable explanation of a risk decision for audit."""
        if not assessment.reasons:
            detail = "no risk signals triggered"
        else:
            detail = "; ".join(assessment.reasons)
        return (
            f"decision={assessment.decision.value} "
            f"score={assessment.score} ({detail})"
        )
