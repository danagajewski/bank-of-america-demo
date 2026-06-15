"""Comprehensive coverage for the fraud detector.

Tests cover every rule, decision boundary, error path, and the end-to-end
score / score_batch / explain flows using synthetic data only.
"""

from datetime import datetime

import pytest

from fraud_detection import FraudDetector, RiskDecision
from fraud_detection.models import RiskAssessment, Transaction


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _txn(
    amount: float = 500.0,
    country: str = "US",
    device_id: str = "device-1",
    customer_id: str = "C-100",
) -> Transaction:
    """Build a synthetic transaction with sensible defaults."""
    return Transaction(
        customer_id=customer_id,
        amount=amount,
        currency="USD",
        country=country,
        device_id=device_id,
        timestamp=datetime(2026, 1, 15, 12, 0, 0),
    )


# ===================================================================
# decide() – threshold boundaries
# ===================================================================

class TestDecide:
    """Verify score-to-decision mapping at every boundary."""

    def test_below_review_threshold_approves(self):
        d = FraudDetector(review_threshold=40, decline_threshold=70)
        assert d.decide(39) == RiskDecision.APPROVE

    def test_at_review_threshold_reviews(self):
        d = FraudDetector(review_threshold=40, decline_threshold=70)
        assert d.decide(40) == RiskDecision.REVIEW

    def test_between_thresholds_reviews(self):
        d = FraudDetector(review_threshold=40, decline_threshold=70)
        assert d.decide(55) == RiskDecision.REVIEW

    def test_one_below_decline_threshold_reviews(self):
        d = FraudDetector(review_threshold=40, decline_threshold=70)
        assert d.decide(69) == RiskDecision.REVIEW

    def test_at_decline_threshold_declines(self):
        d = FraudDetector(review_threshold=40, decline_threshold=70)
        assert d.decide(70) == RiskDecision.DECLINE

    def test_above_decline_threshold_declines(self):
        d = FraudDetector(review_threshold=40, decline_threshold=70)
        assert d.decide(100) == RiskDecision.DECLINE

    def test_zero_score_approves(self):
        d = FraudDetector()
        assert d.decide(0) == RiskDecision.APPROVE


# ===================================================================
# _amount_rule
# ===================================================================

class TestAmountRule:
    """Cover positive / negative / zero / boundary amounts."""

    def test_negative_amount_raises(self):
        d = FraudDetector()
        with pytest.raises(ValueError, match="positive"):
            d._amount_rule(_txn(amount=-1), [])

    def test_zero_amount_raises(self):
        d = FraudDetector()
        with pytest.raises(ValueError, match="positive"):
            d._amount_rule(_txn(amount=0), [])

    def test_below_threshold_scores_zero(self):
        d = FraudDetector(high_amount_threshold=10_000)
        reasons: list[str] = []
        assert d._amount_rule(_txn(amount=9_999.99), reasons) == 0
        assert reasons == []

    def test_at_threshold_scores_45(self):
        d = FraudDetector(high_amount_threshold=10_000)
        reasons: list[str] = []
        assert d._amount_rule(_txn(amount=10_000), reasons) == 45
        assert len(reasons) == 1
        assert "high-amount" in reasons[0]

    def test_above_threshold_scores_45(self):
        d = FraudDetector(high_amount_threshold=10_000)
        reasons: list[str] = []
        assert d._amount_rule(_txn(amount=50_000), reasons) == 45
        assert len(reasons) == 1


# ===================================================================
# _geo_rule
# ===================================================================

class TestGeoRule:
    """Cover home-country vs out-of-country."""

    def test_home_country_scores_zero(self):
        d = FraudDetector(home_country="US")
        reasons: list[str] = []
        assert d._geo_rule(_txn(country="US"), reasons) == 0
        assert reasons == []

    def test_foreign_country_scores_30(self):
        d = FraudDetector(home_country="US")
        reasons: list[str] = []
        assert d._geo_rule(_txn(country="NG"), reasons) == 30
        assert len(reasons) == 1
        assert "out-of-country" in reasons[0]


# ===================================================================
# _velocity_rule
# ===================================================================

class TestVelocityRule:
    """Cover spike threshold boundary."""

    def test_below_spike_threshold_scores_zero(self):
        d = FraudDetector()
        reasons: list[str] = []
        assert d._velocity_rule(_txn(), 4, reasons) == 0
        assert reasons == []

    def test_at_spike_threshold_scores_35(self):
        d = FraudDetector()
        reasons: list[str] = []
        assert d._velocity_rule(_txn(), 5, reasons) == 35
        assert len(reasons) == 1
        assert "velocity" in reasons[0]

    def test_above_spike_threshold_scores_35(self):
        d = FraudDetector()
        reasons: list[str] = []
        assert d._velocity_rule(_txn(), 10, reasons) == 35


# ===================================================================
# _device_rule
# ===================================================================

class TestDeviceRule:
    """Cover trusted / unrecognized / empty-set scenarios."""

    def test_no_trusted_set_scores_zero(self):
        d = FraudDetector(trusted_devices=None)
        reasons: list[str] = []
        assert d._device_rule(_txn(device_id="anything"), reasons) == 0
        assert reasons == []

    def test_empty_trusted_set_scores_zero(self):
        d = FraudDetector(trusted_devices=[])
        reasons: list[str] = []
        assert d._device_rule(_txn(device_id="anything"), reasons) == 0
        assert reasons == []

    def test_trusted_device_scores_zero(self):
        d = FraudDetector(trusted_devices=["device-1"])
        reasons: list[str] = []
        assert d._device_rule(_txn(device_id="device-1"), reasons) == 0
        assert reasons == []

    def test_unrecognized_device_scores_20(self):
        d = FraudDetector(trusted_devices=["device-1"])
        reasons: list[str] = []
        assert d._device_rule(_txn(device_id="device-99"), reasons) == 20
        assert len(reasons) == 1
        assert "unrecognized" in reasons[0]


# ===================================================================
# score() – end-to-end
# ===================================================================

class TestScore:
    """Integration-level tests for the full scoring pipeline."""

    def test_none_transaction_raises(self):
        d = FraudDetector()
        with pytest.raises(ValueError, match="None"):
            d.score(None)

    def test_clean_transaction_approves(self):
        d = FraudDetector()
        result = d.score(_txn(amount=100, country="US"))
        assert result.decision == RiskDecision.APPROVE
        assert result.score == 0
        assert result.reasons == []
        assert result.transaction is not None

    def test_high_amount_foreign_triggers_decline(self):
        d = FraudDetector(high_amount_threshold=10_000)
        result = d.score(_txn(amount=15_000, country="BR"))
        # amount(45) + geo(30) = 75 → DECLINE
        assert result.score == 75
        assert result.decision == RiskDecision.DECLINE
        assert len(result.reasons) == 2

    def test_velocity_plus_device_triggers_review(self):
        d = FraudDetector(trusted_devices=["device-1"])
        txn = _txn(amount=100, country="US", device_id="device-99")
        counts = {txn.customer_id: 6}
        result = d.score(txn, recent_counts=counts)
        # velocity(35) + device(20) = 55 → REVIEW
        assert result.score == 55
        assert result.decision == RiskDecision.REVIEW

    def test_all_rules_fire_caps_at_100(self):
        d = FraudDetector(
            high_amount_threshold=10_000,
            trusted_devices=["device-1"],
        )
        txn = _txn(amount=50_000, country="BR", device_id="device-99")
        counts = {txn.customer_id: 10}
        result = d.score(txn, recent_counts=counts)
        # amount(45) + geo(30) + velocity(35) + device(20) = 130 → capped 100
        assert result.score == 100
        assert result.decision == RiskDecision.DECLINE
        assert len(result.reasons) == 4

    def test_score_aggregates_reasons(self):
        d = FraudDetector(home_country="US")
        result = d.score(_txn(amount=100, country="NG"))
        assert any("out-of-country" in r for r in result.reasons)

    def test_score_with_no_recent_counts(self):
        d = FraudDetector()
        result = d.score(_txn(), recent_counts=None)
        assert result.decision == RiskDecision.APPROVE

    def test_score_returns_risk_assessment(self):
        d = FraudDetector()
        result = d.score(_txn())
        assert isinstance(result, RiskAssessment)
        assert isinstance(result.decision, RiskDecision)


# ===================================================================
# score_batch()
# ===================================================================

class TestScoreBatch:
    """Verify batch scoring returns correct results for each transaction."""

    def test_batch_returns_list_of_assessments(self):
        d = FraudDetector()
        txns = [_txn(amount=100), _txn(amount=200)]
        results = d.score_batch(txns)
        assert len(results) == 2
        assert all(isinstance(r, RiskAssessment) for r in results)

    def test_batch_respects_recent_counts(self):
        d = FraudDetector()
        txn = _txn(amount=100, customer_id="C-200")
        results = d.score_batch([txn], recent_counts={"C-200": 8})
        assert results[0].score == 35  # velocity rule fires


# ===================================================================
# explain()
# ===================================================================

class TestExplain:
    """Verify the audit-trail explanation rendering."""

    def test_explain_no_reasons(self):
        d = FraudDetector()
        assessment = RiskAssessment(
            score=0, decision=RiskDecision.APPROVE, reasons=[]
        )
        text = d.explain(assessment)
        assert "no risk signals" in text
        assert "APPROVE" in text

    def test_explain_with_reasons(self):
        d = FraudDetector()
        assessment = RiskAssessment(
            score=75,
            decision=RiskDecision.DECLINE,
            reasons=["high amount", "out-of-country"],
        )
        text = d.explain(assessment)
        assert "DECLINE" in text
        assert "75" in text
        assert "high amount" in text
        assert "out-of-country" in text
