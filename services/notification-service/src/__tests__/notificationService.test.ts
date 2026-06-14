import { NotificationService } from '../notificationService';
import { AlertGateway, CustomerAlert } from '../types';

const noopGateway: AlertGateway = {
  deliver: () => true,
};

function mockGateway(): AlertGateway & { deliver: jest.Mock } {
  return { deliver: jest.fn().mockReturnValue(true) };
}

function alert(overrides: Partial<CustomerAlert> = {}): CustomerAlert {
  return { type: 'TRANSACTION', customerId: 'cust-1', body: 'hello', ...overrides };
}

// ---------------------------------------------------------------------------
// classifySeverity
// ---------------------------------------------------------------------------
describe('NotificationService.classifySeverity', () => {
  const service = new NotificationService(noopGateway);

  it('treats FRAUD alerts as CRITICAL', () => {
    expect(service.classifySeverity(alert({ type: 'FRAUD' }))).toBe('CRITICAL');
  });

  it('treats REGULATORY alerts as HIGH', () => {
    expect(service.classifySeverity(alert({ type: 'REGULATORY' }))).toBe('HIGH');
  });

  it('treats BALANCE with negative amount as HIGH', () => {
    expect(service.classifySeverity(alert({ type: 'BALANCE', amount: -1 }))).toBe('HIGH');
  });

  it('treats BALANCE with amount === 0 as NORMAL', () => {
    expect(service.classifySeverity(alert({ type: 'BALANCE', amount: 0 }))).toBe('NORMAL');
  });

  it('treats BALANCE with undefined amount as NORMAL', () => {
    expect(service.classifySeverity(alert({ type: 'BALANCE' }))).toBe('NORMAL');
  });

  it('treats BALANCE with positive amount as NORMAL', () => {
    expect(service.classifySeverity(alert({ type: 'BALANCE', amount: 100 }))).toBe('NORMAL');
  });

  it('treats TRANSACTION as NORMAL', () => {
    expect(service.classifySeverity(alert({ type: 'TRANSACTION' }))).toBe('NORMAL');
  });

  it('defaults unknown types to NORMAL', () => {
    expect(service.classifySeverity(alert({ type: 'UNKNOWN' as any }))).toBe('NORMAL');
  });
});

// ---------------------------------------------------------------------------
// channelFor
// ---------------------------------------------------------------------------
describe('NotificationService.channelFor', () => {
  const service = new NotificationService(noopGateway);

  it('routes CRITICAL to PUSH', () => {
    expect(service.channelFor('CRITICAL')).toBe('PUSH');
  });

  it('routes HIGH to SMS', () => {
    expect(service.channelFor('HIGH')).toBe('SMS');
  });

  it('routes NORMAL to EMAIL', () => {
    expect(service.channelFor('NORMAL')).toBe('EMAIL');
  });

  it('defaults unknown severity to EMAIL', () => {
    expect(service.channelFor('UNKNOWN' as any)).toBe('EMAIL');
  });
});

// ---------------------------------------------------------------------------
// redactAccountNumbers — PII masking focus
// ---------------------------------------------------------------------------
describe('NotificationService.redactAccountNumbers', () => {
  const service = new NotificationService(noopGateway);

  it('redacts a 10-digit account number preserving only last 4 digits', () => {
    expect(service.redactAccountNumbers('account 1234567890')).toBe('account ****7890');
  });

  it('never leaks more than 4 trailing digits', () => {
    const result = service.redactAccountNumbers('account 1234567890');
    const trailingDigits = result.replace(/.*\*/, '');
    expect(trailingDigits.length).toBe(4);
  });

  it('raw account number never appears in the output', () => {
    const raw = '9876543210';
    const result = service.redactAccountNumbers(`ref ${raw}`);
    expect(result).not.toContain(raw);
  });

  it('redacts dashed-format account numbers', () => {
    const result = service.redactAccountNumbers('acct 123-456-7890');
    expect(result).not.toContain('123-456-7890');
    expect(result).toContain('****7890');
  });

  it('leaves 4-digit numbers untouched (boundary: at length threshold)', () => {
    expect(service.redactAccountNumbers('pin 1234')).toBe('pin 1234');
  });

  it('redacts 5-digit numbers (boundary: just over threshold)', () => {
    const result = service.redactAccountNumbers('code 12345');
    expect(result).toBe('code ****2345');
  });

  it('redacts multiple account numbers in one body', () => {
    const result = service.redactAccountNumbers('from 1234567890 to 9876543210');
    expect(result).not.toContain('1234567890');
    expect(result).not.toContain('9876543210');
    expect(result).toBe('from ****7890 to ****3210');
  });

  it('handles body with no account numbers', () => {
    expect(service.redactAccountNumbers('no numbers here')).toBe('no numbers here');
  });

  it('handles empty string', () => {
    expect(service.redactAccountNumbers('')).toBe('');
  });
});

// ---------------------------------------------------------------------------
// formatMessage
// ---------------------------------------------------------------------------
describe('NotificationService.formatMessage', () => {
  const service = new NotificationService(noopGateway);

  it('prefixes FRAUD alerts with "Fraud Alert"', () => {
    const msg = service.formatMessage(alert({ type: 'FRAUD', body: 'suspicious activity' }));
    expect(msg).toBe('Fraud Alert: suspicious activity');
  });

  it('prefixes TRANSACTION alerts with "Transaction"', () => {
    const msg = service.formatMessage(alert({ type: 'TRANSACTION', body: 'purchase' }));
    expect(msg).toBe('Transaction: purchase');
  });

  it('prefixes BALANCE alerts with "Balance Alert"', () => {
    const msg = service.formatMessage(alert({ type: 'BALANCE', body: 'low balance' }));
    expect(msg).toBe('Balance Alert: low balance');
  });

  it('prefixes REGULATORY alerts with "Important Notice"', () => {
    const msg = service.formatMessage(alert({ type: 'REGULATORY', body: 'update' }));
    expect(msg).toBe('Important Notice: update');
  });

  it('prefixes unknown types with "Notice"', () => {
    const msg = service.formatMessage(alert({ type: 'OTHER' as any, body: 'info' }));
    expect(msg).toBe('Notice: info');
  });

  it('redacts account numbers in the body', () => {
    const msg = service.formatMessage(alert({ body: 'acct 1234567890' }));
    expect(msg).toContain('****7890');
    expect(msg).not.toContain('1234567890');
  });
});

// ---------------------------------------------------------------------------
// send — end-to-end
// ---------------------------------------------------------------------------
describe('NotificationService.send', () => {
  it('throws when customerId is missing (empty string)', () => {
    const gw = mockGateway();
    const service = new NotificationService(gw);
    expect(() => service.send(alert({ customerId: '' }))).toThrow('customerId is required');
    expect(gw.deliver).not.toHaveBeenCalled();
  });

  it('does not call gateway when customerId is missing', () => {
    const gw = mockGateway();
    const service = new NotificationService(gw);
    expect(() => service.send(alert({ customerId: '' }))).toThrow();
    expect(gw.deliver).not.toHaveBeenCalled();
  });

  it('returns correct channel, severity, and redacted message for FRAUD alert', () => {
    const gw = mockGateway();
    const service = new NotificationService(gw);
    const result = service.send(alert({ type: 'FRAUD', body: 'acct 1234567890' }));
    expect(result.severity).toBe('CRITICAL');
    expect(result.channel).toBe('PUSH');
    expect(result.message).toContain('****7890');
    expect(result.message).not.toContain('1234567890');
    expect(result.delivered).toBe(true);
    expect(gw.deliver).toHaveBeenCalledTimes(1);
  });

  it('calls gateway exactly once with correct arguments', () => {
    const gw = mockGateway();
    const service = new NotificationService(gw);
    service.send(alert({ type: 'TRANSACTION', customerId: 'cust-42', body: 'payment' }));
    expect(gw.deliver).toHaveBeenCalledWith('EMAIL', 'cust-42', 'Transaction: payment');
  });

  it('routes REGULATORY alert through SMS channel', () => {
    const gw = mockGateway();
    const service = new NotificationService(gw);
    const result = service.send(alert({ type: 'REGULATORY', body: 'compliance update' }));
    expect(result.severity).toBe('HIGH');
    expect(result.channel).toBe('SMS');
  });

  it('routes BALANCE with negative amount through SMS', () => {
    const gw = mockGateway();
    const service = new NotificationService(gw);
    const result = service.send(alert({ type: 'BALANCE', amount: -50, body: 'overdraft' }));
    expect(result.severity).toBe('HIGH');
    expect(result.channel).toBe('SMS');
  });

  it('propagates delivered=false from gateway', () => {
    const gw = mockGateway();
    gw.deliver.mockReturnValue(false);
    const service = new NotificationService(gw);
    const result = service.send(alert());
    expect(result.delivered).toBe(false);
  });
});
