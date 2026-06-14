"""Comprehensive coverage for the fraud detector.

Tests exercise every rule boundary on both sides and all error paths.
All data is synthetic.
"""

from datetime import datetime

import pytest

from fraud_detection import FraudDetector, RiskDecision
from fraud_detection.models import RiskAssessment, Transaction


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _txn(
    amount: float = 100.0,
    country: str = "US",
    device_id: str = "device-abc",
    customer_id: str = "cust-1",
) -> Transaction:
    """Factory for synthetic transactions."""
    return Transaction(
        customer_id=customer_id,
        amount=amount,
        currency="USD",
        country=country,
        device_id=device_id,
        timestamp=datetime(2026, 1, 15, 12, 0, 0),
    )


# ===========================================================================
# decide() boundary tests
# ===========================================================================


class TestDecide:
    """Test score-to-decision mapping at exact boundaries."""

    def test_score_exactly_at_decline_threshold(self):
        detector = FraudDetector()
        assert detector.decide(70) == RiskDecision.DECLINE

    def test_score_one_below_decline_threshold(self):
        detector = FraudDetector()
        assert detector.decide(69) == RiskDecision.REVIEW

    def test_score_exactly_at_review_threshold(self):
        detector = FraudDetector()
        assert detector.decide(40) == RiskDecision.REVIEW

    def test_score_one_below_review_threshold(self):
        detector = FraudDetector()
        assert detector.decide(39) == RiskDecision.APPROVE

    def test_score_zero_is_approve(self):
        detector = FraudDetector()
        assert detector.decide(0) == RiskDecision.APPROVE

    def test_score_above_decline_threshold(self):
        detector = FraudDetector()
        assert detector.decide(100) == RiskDecision.DECLINE


# ===========================================================================
# _amount_rule() tests
# ===========================================================================


class TestAmountRule:
    """Boundary tests for the high-amount heuristic."""

    def test_amount_zero_raises_value_error(self):
        detector = FraudDetector()
        with pytest.raises(ValueError, match="must be positive"):
            detector._amount_rule(_txn(amount=0), [])

    def test_negative_amount_raises_value_error(self):
        detector = FraudDetector()
        with pytest.raises(ValueError, match="must be positive"):
            detector._amount_rule(_txn(amount=-50.0), [])

    def test_amount_exactly_at_threshold_returns_45(self):
        detector = FraudDetector()
        reasons: list[str] = []
        assert detector._amount_rule(_txn(amount=10_000.0), reasons) == 45
        assert len(reasons) == 1

    def test_amount_just_below_threshold_returns_0(self):
        detector = FraudDetector()
        reasons: list[str] = []
        assert detector._amount_rule(_txn(amount=9_999.99), reasons) == 0
        assert len(reasons) == 0

    def test_amount_above_threshold_returns_45(self):
        detector = FraudDetector()
        reasons: list[str] = []
        assert detector._amount_rule(_txn(amount=50_000.0), reasons) == 45
        assert len(reasons) == 1


# ===========================================================================
# _geo_rule() tests
# ===========================================================================


class TestGeoRule:
    """Tests for cross-border detection."""

    def test_foreign_country_returns_30(self):
        detector = FraudDetector()
        reasons: list[str] = []
        assert detector._geo_rule(_txn(country="GB"), reasons) == 30
        assert "out-of-country" in reasons[0]

    def test_home_country_returns_0(self):
        detector = FraudDetector()
        reasons: list[str] = []
        assert detector._geo_rule(_txn(country="US"), reasons) == 0
        assert len(reasons) == 0


# ===========================================================================
# _velocity_rule() tests
# ===========================================================================


class TestVelocityRule:
    """Boundary tests for transaction velocity spike."""

    def test_recent_count_exactly_5_returns_35(self):
        detector = FraudDetector()
        reasons: list[str] = []
        assert detector._velocity_rule(_txn(), 5, reasons) == 35
        assert "velocity spike" in reasons[0]

    def test_recent_count_4_returns_0(self):
        detector = FraudDetector()
        reasons: list[str] = []
        assert detector._velocity_rule(_txn(), 4, reasons) == 0
        assert len(reasons) == 0

    def test_recent_count_above_5_returns_35(self):
        detector = FraudDetector()
        reasons: list[str] = []
        assert detector._velocity_rule(_txn(), 10, reasons) == 35
        assert len(reasons) == 1


# ===========================================================================
# _device_rule() tests
# ===========================================================================


class TestDeviceRule:
    """Tests for unrecognized device detection."""

    def test_untrusted_device_returns_20(self):
        detector = FraudDetector(trusted_devices=["device-xyz"])
        reasons: list[str] = []
        assert detector._device_rule(_txn(device_id="device-unknown"), reasons) == 20
        assert "unrecognized device" in reasons[0]

    def test_trusted_device_returns_0(self):
        detector = FraudDetector(trusted_devices=["device-abc"])
        reasons: list[str] = []
        assert detector._device_rule(_txn(device_id="device-abc"), reasons) == 0
        assert len(reasons) == 0

    def test_no_trusted_devices_configured_returns_0(self):
        detector = FraudDetector(trusted_devices=None)
        reasons: list[str] = []
        assert detector._device_rule(_txn(device_id="any-device"), reasons) == 0
        assert len(reasons) == 0

    def test_empty_trusted_set_returns_0(self):
        detector = FraudDetector(trusted_devices=[])
        reasons: list[str] = []
        assert detector._device_rule(_txn(device_id="any-device"), reasons) == 0
        assert len(reasons) == 0


# ===========================================================================
# score() integration tests
# ===========================================================================


class TestScore:
    """End-to-end score() flow with aggregation, cap, and error handling."""

    def test_none_transaction_raises_value_error(self):
        detector = FraudDetector()
        with pytest.raises(ValueError, match="must not be None"):
            detector.score(None)

    def test_amount_only_triggers_review(self):
        """amount=10000 → score=45 → REVIEW."""
        detector = FraudDetector()
        result = detector.score(_txn(amount=10_000.0))
        assert result.score == 45
        assert result.decision == RiskDecision.REVIEW
        assert len(result.reasons) == 1

    def test_amount_plus_geo_triggers_decline(self):
        """amount(45) + geo(30) = 75 → DECLINE."""
        detector = FraudDetector()
        result = detector.score(_txn(amount=10_000.0, country="GB"))
        assert result.score == 75
        assert result.decision == RiskDecision.DECLINE
        assert len(result.reasons) == 2

    def test_total_capped_at_100(self):
        """All rules fire: 45+30+35+20=130 → capped at 100."""
        detector = FraudDetector(trusted_devices=["device-xyz"])
        txn = _txn(amount=50_000.0, country="GB", device_id="device-unknown")
        result = detector.score(txn, recent_counts={"cust-1": 10})
        assert result.score == 100
        assert result.decision == RiskDecision.DECLINE
        assert len(result.reasons) == 4

    def test_clean_transaction_approve(self):
        """All rules pass → score=0 → APPROVE."""
        detector = FraudDetector()
        result = detector.score(_txn(amount=100.0, country="US"))
        assert result.score == 0
        assert result.decision == RiskDecision.APPROVE
        assert len(result.reasons) == 0

    def test_reasons_aggregate_correctly(self):
        """Geo + velocity → 2 reasons recorded."""
        detector = FraudDetector()
        result = detector.score(
            _txn(amount=50.0, country="CA"),
            recent_counts={"cust-1": 6},
        )
        assert result.score == 65
        assert result.decision == RiskDecision.REVIEW
        assert len(result.reasons) == 2

    def test_recent_counts_none_defaults_to_zero(self):
        detector = FraudDetector()
        result = detector.score(_txn(), recent_counts=None)
        assert result.score == 0

    def test_customer_not_in_recent_counts(self):
        detector = FraudDetector()
        result = detector.score(_txn(customer_id="cust-99"), recent_counts={"cust-1": 10})
        assert result.score == 0


# ===========================================================================
# score_batch() tests
# ===========================================================================


class TestScoreBatch:
    """Tests for batch scoring."""

    def test_empty_batch_returns_empty(self):
        detector = FraudDetector()
        assert detector.score_batch([]) == []

    def test_batch_scores_multiple_transactions(self):
        detector = FraudDetector()
        txns = [_txn(amount=100.0), _txn(amount=10_000.0)]
        results = detector.score_batch(txns)
        assert len(results) == 2
        assert results[0].decision == RiskDecision.APPROVE
        assert results[1].decision == RiskDecision.REVIEW


# ===========================================================================
# explain() tests
# ===========================================================================


class TestExplain:
    """Tests for audit explanation rendering."""

    def test_explain_with_no_reasons(self):
        detector = FraudDetector()
        assessment = RiskAssessment(
            score=0, decision=RiskDecision.APPROVE, reasons=[]
        )
        explanation = detector.explain(assessment)
        assert "no risk signals triggered" in explanation
        assert "APPROVE" in explanation

    def test_explain_with_multiple_reasons(self):
        detector = FraudDetector()
        assessment = RiskAssessment(
            score=75,
            decision=RiskDecision.DECLINE,
            reasons=["high amount", "foreign country"],
        )
        explanation = detector.explain(assessment)
        assert "high amount; foreign country" in explanation
        assert "DECLINE" in explanation
        assert "score=75" in explanation
