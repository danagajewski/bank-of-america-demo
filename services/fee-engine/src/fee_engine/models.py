"""Domain models for fee assessment."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import List


class FeeType(str, Enum):
    """Category of an assessed fee."""

    OVERDRAFT = "OVERDRAFT"
    MONTHLY_MAINTENANCE = "MONTHLY_MAINTENANCE"
    ATM_OUT_OF_NETWORK = "ATM_OUT_OF_NETWORK"
    WIRE_TRANSFER = "WIRE_TRANSFER"


@dataclass
class Fee:
    """A single assessed fee."""

    type: FeeType
    amount: float
    description: str


@dataclass
class AccountActivity:
    """The monthly activity inputs used to assess fees. Synthetic data only."""

    average_balance: float
    ending_balance: float
    overdraft_count: int = 0
    out_of_network_atm_count: int = 0
    wire_transfer_count: int = 0


@dataclass
class FeeAssessment:
    """The result of assessing fees for an account's activity."""

    fees: List[Fee] = field(default_factory=list)

    @property
    def total(self) -> float:
        return round(sum(fee.amount for fee in self.fees), 2)
