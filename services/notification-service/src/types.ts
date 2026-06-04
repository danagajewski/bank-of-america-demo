export type AlertType = 'FRAUD' | 'TRANSACTION' | 'BALANCE' | 'REGULATORY';

export type Severity = 'CRITICAL' | 'HIGH' | 'NORMAL';

export type Channel = 'PUSH' | 'SMS' | 'EMAIL';

export interface CustomerAlert {
  type: AlertType;
  customerId: string;
  /** Free-text body that may reference an account number; must be redacted before send. */
  body: string;
  accountNumber?: string;
  amount?: number;
}

export interface DeliveryResult {
  delivered: boolean;
  channel: Channel;
  severity: Severity;
  message: string;
}

export interface AlertGateway {
  deliver(channel: Channel, customerId: string, message: string): boolean;
}
