import { Pipe, PipeTransform } from '@angular/core';

const FORMAT = new Intl.NumberFormat('en', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * Formats a money amount with thousands separators and two decimals. There is deliberately NO currency symbol: the backend has no
 * currency field, only a market code, so templates show the market next to the figure instead of guessing a currency.
 */
@Pipe({ name: 'amount' })
export class AmountPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return value === null || value === undefined ? '—' : FORMAT.format(value);
  }
}
