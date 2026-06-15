import { NotificationService } from '../notificationService';
import { AlertGateway, CustomerAlert } from '../types';

/* ------------------------------------------------------------------ */
/*  Shared helpers                                                     */
/* ------------------------------------------------------------------ */

const noopGateway: AlertGateway = {
  deliver: () => true,
};

function makeAlert(overrides: Partial<CustomerAlert> = {}): CustomerAlert {
  return {
    type: 'TRANSACTION',
    customerId: 'cust-001',
    body: 'Your recent activity',
    ...overrides,
  };
}

/* ================================================================== */
/*  classifySeverity                                                   */
/* ================================================================== */

describe('NotificationService.classifySeverity', () => {
  const service = new NotificationService(noopGateway);

  it('FRAUD -> CRITICAL', () => {
    expect(service.classifySeverity(makeAlert({ type: 'FRAUD' }))).toBe('CRITICAL');
  });

  it('REGULATORY -> HIGH', () => {
    expect(service.classifySeverity(makeAlert({ type: 'REGULATORY' }))).toBe('HIGH');
  });

  it('BALANCE with negative amount -> HIGH', () => {
    expect(
      service.classifySeverity(makeAlert({ type: 'BALANCE', amount: -150.0 })),
    ).toBe('HIGH');
  });

  it('BALANCE with non-negative amount -> NORMAL', () => {
    expect(
      service.classifySeverity(makeAlert({ type: 'BALANCE', amount: 500 })),
    ).toBe('NORMAL');
  });

  it('BALANCE with zero amount -> NORMAL', () => {
    expect(
      service.classifySeverity(makeAlert({ type: 'BALANCE', amount: 0 })),
    ).toBe('NORMAL');
  });

  it('BALANCE with undefined amount -> NORMAL', () => {
    expect(
      service.classifySeverity(makeAlert({ type: 'BALANCE' })),
    ).toBe('NORMAL');
  });

  it('TRANSACTION -> NORMAL', () => {
    expect(service.classifySeverity(makeAlert({ type: 'TRANSACTION' }))).toBe('NORMAL');
  });
});

/* ================================================================== */
/*  channelFor                                                         */
/* ================================================================== */

describe('NotificationService.channelFor', () => {
  const service = new NotificationService(noopGateway);

  it('CRITICAL -> PUSH', () => {
    expect(service.channelFor('CRITICAL')).toBe('PUSH');
  });

  it('HIGH -> SMS', () => {
    expect(service.channelFor('HIGH')).toBe('SMS');
  });

  it('NORMAL -> EMAIL', () => {
    expect(service.channelFor('NORMAL')).toBe('EMAIL');
  });
});

/* ================================================================== */
/*  redactAccountNumbers — PII / compliance                            */
/* ================================================================== */

describe('NotificationService.redactAccountNumbers', () => {
  const service = new NotificationService(noopGateway);

  it('masks a 10-digit account number exposing only the last 4 digits', () => {
    const result = service.redactAccountNumbers('account 1234567890');
    expect(result).toBe('account ****7890');
  });

  it('output never contains the original account number', () => {
    const raw = '1234567890';
    const result = service.redactAccountNumbers(`account ${raw}`);
    expect(result).not.toContain(raw);
  });

  it('output never exposes more than four trailing digits', () => {
    const result = service.redactAccountNumbers('account 1234567890');
    // After the mask, only exactly 4 digits should follow the asterisks
    const masked = result.match(/\*{4}(\d+)/);
    expect(masked).not.toBeNull();
    expect(masked![1]).toHaveLength(4);
  });

  it('masks a hyphenated account number preserving only last 4 digits', () => {
    const result = service.redactAccountNumbers('acct 1234-5678-9012');
    expect(result).toBe('acct ****9012');
  });

  it('leaves short digit groups (fewer than 5 digits) untouched', () => {
    expect(service.redactAccountNumbers('code 1234')).toBe('code 1234');
  });

  it('masks multiple account numbers in a single body', () => {
    const result = service.redactAccountNumbers(
      'from 1234567890 to 9876543210',
    );
    expect(result).toBe('from ****7890 to ****3210');
  });

  it('returns body unchanged when no account numbers are present', () => {
    const body = 'Welcome to mobile banking!';
    expect(service.redactAccountNumbers(body)).toBe(body);
  });
});

/* ================================================================== */
/*  formatMessage                                                      */
/* ================================================================== */

describe('NotificationService.formatMessage', () => {
  const service = new NotificationService(noopGateway);

  it('prefixes FRAUD alerts with "Fraud Alert"', () => {
    const msg = service.formatMessage(
      makeAlert({ type: 'FRAUD', body: 'suspicious activity' }),
    );
    expect(msg).toBe('Fraud Alert: suspicious activity');
  });

  it('prefixes TRANSACTION alerts with "Transaction"', () => {
    const msg = service.formatMessage(
      makeAlert({ type: 'TRANSACTION', body: 'purchase completed' }),
    );
    expect(msg).toBe('Transaction: purchase completed');
  });

  it('prefixes BALANCE alerts with "Balance Alert"', () => {
    const msg = service.formatMessage(
      makeAlert({ type: 'BALANCE', body: 'low balance warning' }),
    );
    expect(msg).toBe('Balance Alert: low balance warning');
  });

  it('prefixes REGULATORY alerts with "Important Notice"', () => {
    const msg = service.formatMessage(
      makeAlert({ type: 'REGULATORY', body: 'policy update' }),
    );
    expect(msg).toBe('Important Notice: policy update');
  });

  it('redacts account numbers inside the formatted message', () => {
    const msg = service.formatMessage(
      makeAlert({ type: 'FRAUD', body: 'charge on 1234567890' }),
    );
    expect(msg).toBe('Fraud Alert: charge on ****7890');
    expect(msg).not.toContain('1234567890');
  });
});

/* ================================================================== */
/*  send — end-to-end delivery                                         */
/* ================================================================== */

describe('NotificationService.send', () => {
  it('throws when customerId is missing', () => {
    const service = new NotificationService(noopGateway);
    expect(() =>
      service.send(makeAlert({ customerId: '' })),
    ).toThrow('customerId is required');
  });

  it('delivers a FRAUD alert via PUSH at CRITICAL severity with redacted body', () => {
    const gateway: AlertGateway = { deliver: jest.fn().mockReturnValue(true) };
    const service = new NotificationService(gateway);

    const result = service.send(
      makeAlert({
        type: 'FRAUD',
        customerId: 'cust-42',
        body: 'charge on 9876543210',
      }),
    );

    expect(result.severity).toBe('CRITICAL');
    expect(result.channel).toBe('PUSH');
    expect(result.delivered).toBe(true);
    expect(result.message).toBe('Fraud Alert: charge on ****3210');
    expect(result.message).not.toContain('9876543210');
    expect(gateway.deliver).toHaveBeenCalledWith(
      'PUSH',
      'cust-42',
      'Fraud Alert: charge on ****3210',
    );
  });

  it('delivers a REGULATORY alert via SMS at HIGH severity', () => {
    const gateway: AlertGateway = { deliver: jest.fn().mockReturnValue(true) };
    const service = new NotificationService(gateway);

    const result = service.send(
      makeAlert({ type: 'REGULATORY', customerId: 'cust-99', body: 'update' }),
    );

    expect(result.severity).toBe('HIGH');
    expect(result.channel).toBe('SMS');
    expect(result.delivered).toBe(true);
    expect(gateway.deliver).toHaveBeenCalledWith('SMS', 'cust-99', 'Important Notice: update');
  });

  it('delivers a TRANSACTION alert via EMAIL at NORMAL severity', () => {
    const gateway: AlertGateway = { deliver: jest.fn().mockReturnValue(true) };
    const service = new NotificationService(gateway);

    const result = service.send(
      makeAlert({ type: 'TRANSACTION', customerId: 'cust-7', body: 'purchase $25' }),
    );

    expect(result.severity).toBe('NORMAL');
    expect(result.channel).toBe('EMAIL');
    expect(result.delivered).toBe(true);
    expect(gateway.deliver).toHaveBeenCalledWith('EMAIL', 'cust-7', 'Transaction: purchase $25');
  });

  it('delivers a BALANCE alert with negative amount via SMS at HIGH severity', () => {
    const gateway: AlertGateway = { deliver: jest.fn().mockReturnValue(true) };
    const service = new NotificationService(gateway);

    const result = service.send(
      makeAlert({ type: 'BALANCE', customerId: 'cust-3', body: 'overdraft', amount: -50 }),
    );

    expect(result.severity).toBe('HIGH');
    expect(result.channel).toBe('SMS');
    expect(result.delivered).toBe(true);
  });

  it('returns delivered=false when gateway rejects', () => {
    const gateway: AlertGateway = { deliver: jest.fn().mockReturnValue(false) };
    const service = new NotificationService(gateway);

    const result = service.send(makeAlert({ customerId: 'cust-1' }));
    expect(result.delivered).toBe(false);
  });
});
