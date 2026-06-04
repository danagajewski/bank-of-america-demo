import { NotificationService } from '../notificationService';
import { AlertGateway } from '../types';

/**
 * Baseline coverage for the notification service. Only severity classification is
 * covered today. The compliance-critical paths -- account-number redaction in
 * message bodies and the end-to-end send() flow -- are intentionally NOT yet tested.
 */
const noopGateway: AlertGateway = {
  deliver: () => true,
};

describe('NotificationService.classifySeverity', () => {
  const service = new NotificationService(noopGateway);

  it('treats fraud alerts as CRITICAL', () => {
    expect(
      service.classifySeverity({ type: 'FRAUD', customerId: 'cust-1', body: 'x' })
    ).toBe('CRITICAL');
  });

  it('treats routine transactions as NORMAL', () => {
    expect(
      service.classifySeverity({ type: 'TRANSACTION', customerId: 'cust-1', body: 'x' })
    ).toBe('NORMAL');
  });
});

describe('NotificationService.channelFor', () => {
  const service = new NotificationService(noopGateway);

  it('routes critical alerts to push', () => {
    expect(service.channelFor('CRITICAL')).toBe('PUSH');
  });

  it('routes normal alerts to email', () => {
    expect(service.channelFor('NORMAL')).toBe('EMAIL');
  });
});
