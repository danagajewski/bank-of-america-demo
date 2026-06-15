import { NotificationService } from '../notificationService';
import { AlertGateway, CustomerAlert } from '../types';

const noopGateway: AlertGateway = {
  deliver: () => true,
};

// ---------------------------------------------------------------------------
// classifySeverity
// ---------------------------------------------------------------------------
describe('NotificationService.classifySeverity', () => {
  const service = new NotificationService(noopGateway);

  it('treats FRAUD alerts as CRITICAL', () => {
    expect(
      service.classifySeverity({ type: 'FRAUD', customerId: 'cust-1', body: 'x' }),
    ).toBe('CRITICAL');
  });

  it('treats REGULATORY alerts as HIGH', () => {
    expect(
      service.classifySeverity({ type: 'REGULATORY', customerId: 'cust-1', body: 'x' }),
    ).toBe('HIGH');
  });

  it('treats BALANCE with negative amount as HIGH', () => {
    expect(
      service.classifySeverity({
        type: 'BALANCE',
        customerId: 'cust-1',
        body: 'x',
        amount: -50,
      }),
    ).toBe('HIGH');
  });

  it('treats BALANCE with non-negative amount as NORMAL', () => {
    expect(
      service.classifySeverity({
        type: 'BALANCE',
        customerId: 'cust-1',
        body: 'x',
        amount: 100,
      }),
    ).toBe('NORMAL');
  });

  it('treats BALANCE with zero amount as NORMAL', () => {
    expect(
      service.classifySeverity({
        type: 'BALANCE',
        customerId: 'cust-1',
        body: 'x',
        amount: 0,
      }),
    ).toBe('NORMAL');
  });

  it('treats BALANCE with undefined amount as NORMAL', () => {
    expect(
      service.classifySeverity({ type: 'BALANCE', customerId: 'cust-1', body: 'x' }),
    ).toBe('NORMAL');
  });

  it('treats TRANSACTION alerts as NORMAL', () => {
    expect(
      service.classifySeverity({ type: 'TRANSACTION', customerId: 'cust-1', body: 'x' }),
    ).toBe('NORMAL');
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
});

// ---------------------------------------------------------------------------
// redactAccountNumbers — compliance / PII contract
// ---------------------------------------------------------------------------
describe('NotificationService.redactAccountNumbers', () => {
  const service = new NotificationService(noopGateway);

  it('masks a 10-digit account number exposing only the last 4 digits', () => {
    const result = service.redactAccountNumbers('account 1234567890');
    expect(result).toBe('account ****7890');
  });

  it('never contains the original account number', () => {
    const result = service.redactAccountNumbers('account 1234567890');
    expect(result).not.toContain('1234567890');
  });

  it('does not expose more than four trailing digits', () => {
    const result = service.redactAccountNumbers('account 1234567890');
    // Extract everything after the mask prefix
    const masked = result.replace('account ', '');
    const trailingDigits = masked.replace(/^\*+/, '');
    expect(trailingDigits.length).toBe(4);
  });

  it('masks multiple account numbers in the same string', () => {
    const result = service.redactAccountNumbers(
      'from 9876543210 to 1234567890',
    );
    expect(result).toBe('from ****3210 to ****7890');
  });

  it('leaves short digit sequences (< 5 digits) untouched', () => {
    expect(service.redactAccountNumbers('code 1234')).toBe('code 1234');
  });

  it('masks a 5-digit number exposing only the last 4', () => {
    const result = service.redactAccountNumbers('ref 12345');
    expect(result).toBe('ref ****2345');
  });

  it('handles hyphenated account numbers', () => {
    const result = service.redactAccountNumbers('acct 123-456-7890');
    expect(result).toBe('acct ****7890');
  });

  it('returns body unchanged when no account numbers present', () => {
    expect(service.redactAccountNumbers('hello world')).toBe('hello world');
  });
});

// ---------------------------------------------------------------------------
// formatMessage
// ---------------------------------------------------------------------------
describe('NotificationService.formatMessage', () => {
  const service = new NotificationService(noopGateway);

  it('prefixes FRAUD alerts with "Fraud Alert"', () => {
    const msg = service.formatMessage({
      type: 'FRAUD',
      customerId: 'c1',
      body: 'suspicious activity',
    });
    expect(msg).toBe('Fraud Alert: suspicious activity');
  });

  it('prefixes TRANSACTION alerts with "Transaction"', () => {
    const msg = service.formatMessage({
      type: 'TRANSACTION',
      customerId: 'c1',
      body: 'payment received',
    });
    expect(msg).toBe('Transaction: payment received');
  });

  it('prefixes BALANCE alerts with "Balance Alert"', () => {
    const msg = service.formatMessage({
      type: 'BALANCE',
      customerId: 'c1',
      body: 'low balance',
    });
    expect(msg).toBe('Balance Alert: low balance');
  });

  it('prefixes REGULATORY alerts with "Important Notice"', () => {
    const msg = service.formatMessage({
      type: 'REGULATORY',
      customerId: 'c1',
      body: 'policy update',
    });
    expect(msg).toBe('Important Notice: policy update');
  });

  it('redacts account numbers in the formatted message', () => {
    const msg = service.formatMessage({
      type: 'FRAUD',
      customerId: 'c1',
      body: 'card ending 9876543210',
    });
    expect(msg).toBe('Fraud Alert: card ending ****3210');
  });
});

// ---------------------------------------------------------------------------
// send — end-to-end with mocked AlertGateway
// ---------------------------------------------------------------------------
describe('NotificationService.send', () => {
  it('throws when customerId is missing', () => {
    const service = new NotificationService(noopGateway);
    const alert = { type: 'FRAUD', customerId: '', body: 'x' } as CustomerAlert;
    expect(() => service.send(alert)).toThrow('customerId is required');
  });

  it('delivers a FRAUD alert via PUSH at CRITICAL severity with redacted body', () => {
    const mockGateway: AlertGateway = { deliver: jest.fn().mockReturnValue(true) };
    const service = new NotificationService(mockGateway);

    const alert: CustomerAlert = {
      type: 'FRAUD',
      customerId: 'cust-42',
      body: 'charge on 9876543210',
    };

    const result = service.send(alert);

    expect(result.channel).toBe('PUSH');
    expect(result.severity).toBe('CRITICAL');
    expect(result.delivered).toBe(true);
    expect(result.message).toBe('Fraud Alert: charge on ****3210');
    expect(mockGateway.deliver).toHaveBeenCalledWith(
      'PUSH',
      'cust-42',
      'Fraud Alert: charge on ****3210',
    );
  });

  it('delivers a REGULATORY alert via SMS at HIGH severity', () => {
    const mockGateway: AlertGateway = { deliver: jest.fn().mockReturnValue(true) };
    const service = new NotificationService(mockGateway);

    const result = service.send({
      type: 'REGULATORY',
      customerId: 'cust-7',
      body: 'terms updated',
    });

    expect(result.channel).toBe('SMS');
    expect(result.severity).toBe('HIGH');
    expect(result.delivered).toBe(true);
    expect(mockGateway.deliver).toHaveBeenCalledWith(
      'SMS',
      'cust-7',
      'Important Notice: terms updated',
    );
  });

  it('delivers a TRANSACTION alert via EMAIL at NORMAL severity', () => {
    const mockGateway: AlertGateway = { deliver: jest.fn().mockReturnValue(true) };
    const service = new NotificationService(mockGateway);

    const result = service.send({
      type: 'TRANSACTION',
      customerId: 'cust-9',
      body: 'payment $50',
    });

    expect(result.channel).toBe('EMAIL');
    expect(result.severity).toBe('NORMAL');
    expect(result.delivered).toBe(true);
  });

  it('delivers a negative BALANCE alert via SMS at HIGH severity', () => {
    const mockGateway: AlertGateway = { deliver: jest.fn().mockReturnValue(true) };
    const service = new NotificationService(mockGateway);

    const result = service.send({
      type: 'BALANCE',
      customerId: 'cust-3',
      body: 'overdraft warning',
      amount: -200,
    });

    expect(result.channel).toBe('SMS');
    expect(result.severity).toBe('HIGH');
  });

  it('returns delivered=false when gateway rejects', () => {
    const mockGateway: AlertGateway = { deliver: jest.fn().mockReturnValue(false) };
    const service = new NotificationService(mockGateway);

    const result = service.send({
      type: 'TRANSACTION',
      customerId: 'cust-5',
      body: 'test',
    });

    expect(result.delivered).toBe(false);
  });
});
