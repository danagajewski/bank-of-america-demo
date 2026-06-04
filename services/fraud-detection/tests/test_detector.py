"""Baseline coverage for the fraud detector.

Only the score-to-decision mapping is covered today. The compliance-critical
paths -- the individual amount / geo / velocity rules and the end-to-end score()
flow with its error handling -- are intentionally NOT yet tested.
"""

from fraud_detection import FraudDetector, RiskDecision


def test_decide_maps_low_score_to_approve():
    detector = FraudDetector()
    assert detector.decide(0) == RiskDecision.APPROVE


def test_decide_maps_high_score_to_decline():
    detector = FraudDetector()
    assert detector.decide(85) == RiskDecision.DECLINE
