import { describe, it, expect, beforeEach } from 'vitest';
import { readCookie } from './client';

function clearAllCookies() {
  for (const c of document.cookie.split(';')) {
    const name = c.split('=')[0].trim();
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
  }
}

describe('readCookie', () => {
  beforeEach(clearAllCookies);

  it('zwraca null gdy cookie nie istnieje', () => {
    expect(readCookie('BRAK')).toBeNull();
  });

  it('czyta wartość istniejącego cookie', () => {
    document.cookie = 'XSRF-TOKEN=tok123';
    expect(readCookie('XSRF-TOKEN')).toBe('tok123');
  });

  it('dekoduje wartość URL-encoded', () => {
    document.cookie = 'X=a%20b%2Bc';
    expect(readCookie('X')).toBe('a b+c');
  });

  it('wybiera właściwe cookie spośród wielu', () => {
    document.cookie = 'A=1';
    document.cookie = 'SESSION=sess';
    document.cookie = 'B=2';
    expect(readCookie('SESSION')).toBe('sess');
  });
});
