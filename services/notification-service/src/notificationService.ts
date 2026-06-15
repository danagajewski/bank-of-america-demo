import {
  AlertGateway,
  AlertType,
  Channel,
  CustomerAlert,
  DeliveryResult,
  Severity,
} from './types';

/**
 * Real-time customer alerting. Routes fraud, transaction, balance, and regulatory
 * notifications to the right channel at the right urgency, and guarantees account
 * numbers are redacted out of every message body before it leaves the service.
 */
export class NotificationService {
  constructor(private readonly gateway: AlertGateway) {}

  /** Map an alert to a delivery severity. */
  classifySeverity(alert: CustomerAlert): Severity {
    switch (alert.type) {
      case 'FRAUD':
        return 'CRITICAL';
      case 'REGULATORY':
        return 'HIGH';
      case 'BALANCE':
        return alert.amount !== undefined && alert.amount < 0 ? 'HIGH' : 'NORMAL';
      case 'TRANSACTION':
      default:
        return 'NORMAL';
    }
  }

  /** Choose the delivery channel based on alert urgency. */
  channelFor(severity: Severity): Channel {
    switch (severity) {
      case 'CRITICAL':
        return 'PUSH';
      case 'HIGH':
        return 'SMS';
      case 'NORMAL':
      default:
        return 'EMAIL';
    }
  }

  /**
   * Redact any embedded account number, preserving only the last four digits.
   * Raw account numbers must never appear in a delivered message or in logs.
   */
  redactAccountNumbers(body: string): string {
    return body.replace(/\b(\d[\d-]{4,})\b/g, (match) => {
      const digits = match.replace(/\D/g, '');
      if (digits.length < 5) {
        return match;
      }
      const last4 = digits.slice(-4);
      return `****${last4}`;
    });
  }

  /** Compose the final, redacted message body for an alert. */
  formatMessage(alert: CustomerAlert): string {
    const prefix = NotificationService.prefixFor(alert.type);
    return `${prefix}: ${this.redactAccountNumbers(alert.body)}`;
  }

  /** Validate, format, and deliver an alert. */
  send(alert: CustomerAlert): DeliveryResult {
    if (!alert.customerId) {
      throw new Error('customerId is required');
    }
    const severity = this.classifySeverity(alert);
    const channel = this.channelFor(severity);
    const message = this.formatMessage(alert);
    const delivered = this.gateway.deliver(channel, alert.customerId, message);
    return { delivered, channel, severity, message };
  }

  private static prefixFor(type: AlertType): string {
    switch (type) {
      case 'FRAUD':
        return 'Fraud Alert';
      case 'TRANSACTION':
        return 'Transaction';
      case 'BALANCE':
        return 'Balance Alert';
      case 'REGULATORY':
        return 'Important Notice';
      default:
        return 'Notice';
    }
  }
}
