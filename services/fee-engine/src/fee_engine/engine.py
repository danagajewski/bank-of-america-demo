"""Fee assessment engine.

Translates a month of account activity into a deterministic, itemized set of
fees. Every fee carries a human-readable description so a customer dispute or an
examiner can trace exactly why it was charged.
"""

from __future__ import annotations

from typing import List

from .models import AccountActivity, Fee, FeeAssessment, FeeType


class FeeEngine:
    """Assess account and transaction fees from monthly activity."""

    def __init__(
        self,
        overdraft_fee: float = 35.0,
        monthly_maintenance_fee: float = 12.0,
        maintenance_waiver_balance: float = 1_500.0,
        atm_fee: float = 2.5,
        wire_fee: float = 30.0,
    ) -> None:
        self.overdraft_fee = overdraft_fee
        self.monthly_maintenance_fee = monthly_maintenance_fee
        self.maintenance_waiver_balance = maintenance_waiver_balance
        self.atm_fee = atm_fee
        self.wire_fee = wire_fee

    def maintenance_fee(self, average_balance: float) -> float:
        """Monthly maintenance fee, waived above the minimum average balance."""
        if average_balance >= self.maintenance_waiver_balance:
            return 0.0
        return self.monthly_maintenance_fee

    def overdraft_fees(self, overdraft_count: int) -> float:
        if overdraft_count < 0:
            raise ValueError("overdraft_count must not be negative")
        return round(self.overdraft_fee * overdraft_count, 2)

    def atm_fees(self, out_of_network_count: int) -> float:
        if out_of_network_count < 0:
            raise ValueError("out_of_network_count must not be negative")
        return round(self.atm_fee * out_of_network_count, 2)

    def wire_fees(self, wire_count: int) -> float:
        if wire_count < 0:
            raise ValueError("wire_count must not be negative")
        return round(self.wire_fee * wire_count, 2)

    def assess(self, activity: AccountActivity) -> FeeAssessment:
        """Produce an itemized fee assessment for a month of activity."""
        if activity is None:
            raise ValueError("activity must not be None")

        fees: List[Fee] = []

        maintenance = self.maintenance_fee(activity.average_balance)
        if maintenance > 0:
            fees.append(
                Fee(
                    FeeType.MONTHLY_MAINTENANCE,
                    maintenance,
                    f"average balance {activity.average_balance} below waiver "
                    f"{self.maintenance_waiver_balance}",
                )
            )

        overdraft = self.overdraft_fees(activity.overdraft_count)
        if overdraft > 0:
            fees.append(
                Fee(FeeType.OVERDRAFT, overdraft, f"{activity.overdraft_count} overdraft(s)")
            )

        atm = self.atm_fees(activity.out_of_network_atm_count)
        if atm > 0:
            fees.append(
                Fee(
                    FeeType.ATM_OUT_OF_NETWORK,
                    atm,
                    f"{activity.out_of_network_atm_count} out-of-network ATM use(s)",
                )
            )

        wire = self.wire_fees(activity.wire_transfer_count)
        if wire > 0:
            fees.append(
                Fee(FeeType.WIRE_TRANSFER, wire, f"{activity.wire_transfer_count} wire(s)")
            )

        return FeeAssessment(fees=fees)
