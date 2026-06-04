"""Baseline coverage for the fee engine.

Only the monthly-maintenance waiver logic is covered today. The overdraft, ATM,
and wire fee rules, their negative-input guards, and the end-to-end assess()
itemization are intentionally NOT yet tested.
"""

from fee_engine import FeeEngine


def test_maintenance_fee_waived_above_minimum_balance():
    engine = FeeEngine()
    assert engine.maintenance_fee(2_000.0) == 0.0


def test_maintenance_fee_charged_below_minimum_balance():
    engine = FeeEngine()
    assert engine.maintenance_fee(500.0) == 12.0
