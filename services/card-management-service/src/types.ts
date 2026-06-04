export type CardStatus = 'ACTIVE' | 'LOCKED' | 'EXPIRED';

export interface Card {
  cardId: string;
  /** Last four digits only -- a full PAN must never be stored here. */
  last4: string;
  status: CardStatus;
  /** Per-transaction spend limit in minor units (cents). */
  spendLimitCents: number;
}

export interface AuthorizationRequest {
  cardId: string;
  amountCents: number;
  merchant: string;
}

export interface AuthorizationResult {
  approved: boolean;
  reason: string;
}
