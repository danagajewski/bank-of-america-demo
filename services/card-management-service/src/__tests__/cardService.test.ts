import { CardManagementService } from '../cardService';
import { Card } from '../types';

/**
 * Baseline coverage for card management. Only lock/unlock is covered today. The
 * spend-limit setter and the authorization decision path are intentionally NOT
 * yet tested.
 */
function activeCard(): Card {
  return { cardId: 'card-demo-1', last4: '4242', status: 'ACTIVE', spendLimitCents: 50000 };
}

describe('CardManagementService lock/unlock', () => {
  it('locks an active card', () => {
    const service = new CardManagementService();
    service.register(activeCard());
    expect(service.lock('card-demo-1').status).toBe('LOCKED');
  });

  it('unlocks a locked card back to active', () => {
    const service = new CardManagementService();
    service.register(activeCard());
    service.lock('card-demo-1');
    expect(service.unlock('card-demo-1').status).toBe('ACTIVE');
  });
});
