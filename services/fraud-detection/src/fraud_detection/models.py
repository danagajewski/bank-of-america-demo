"""Domain models for fraud risk scoring."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import List, Optional


class RiskDecision(str, Enum):
    """Outcome of a risk assessment."""

    APPROVE = "APPROVE"
    REVIEW = "REVIEW"
    DECLINE = "DECLINE"


@dataclass
class Transaction:
    """A customer transaction submitted for risk scoring.

    Account identifiers here are synthetic only.
    """

    customer_id: str
    amount: float
    currency: str
    country: str
    device_id: str
    timestamp: datetime


@dataclass
class RiskAssessment:
    """The result of scoring a transaction."""

    score: int
    decision: RiskDecision
    reasons: List[str] = field(default_factory=list)
    transaction: Optional[Transaction] = None
