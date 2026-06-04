import { AuthorizationRequest, AuthorizationResult, Card } from './types';

/**
 * Customer-facing card controls. Customers can lock/unlock a card and set a
 * per-transaction spend limit; every authorization is checked against the card's
 * status and limit. Card numbers are referenced by last-four only.
 */
export class CardManagementService {
  private readonly cards = new Map<string, Card>();

  register(card: Card): void {
    this.cards.set(card.cardId, card);
  }

  get(cardId: string): Card {
    const card = this.cards.get(cardId);
    if (!card) {
      throw new Error(`unknown card ${cardId}`);
    }
    return card;
  }

  lock(cardId: string): Card {
    const card = this.get(cardId);
    card.status = 'LOCKED';
    return card;
  }

  unlock(cardId: string): Card {
    const card = this.get(cardId);
    if (card.status === 'EXPIRED') {
      throw new Error('cannot unlock an expired card');
    }
    card.status = 'ACTIVE';
    return card;
  }

  setSpendLimit(cardId: string, spendLimitCents: number): Card {
    if (spendLimitCents <= 0) {
      throw new Error('spend limit must be positive');
    }
    const card = this.get(cardId);
    card.spendLimitCents = spendLimitCents;
    return card;
  }

  /** Authorize a purchase against the card's status and spend limit. */
  authorize(request: AuthorizationRequest): AuthorizationResult {
    const card = this.get(request.cardId);
    if (card.status !== 'ACTIVE') {
      return { approved: false, reason: `card is ${card.status}` };
    }
    if (request.amountCents <= 0) {
      return { approved: false, reason: 'invalid amount' };
    }
    if (request.amountCents > card.spendLimitCents) {
      return { approved: false, reason: 'over spend limit' };
    }
    return { approved: true, reason: 'approved' };
  }
}
