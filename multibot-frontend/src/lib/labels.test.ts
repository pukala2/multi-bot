import { describe, it, expect } from 'vitest';
import { documentStatusLabel, documentStatusVariant } from './labels';

describe('documentStatusLabel', () => {
  it('tłumaczy znane statusy na polski', () => {
    expect(documentStatusLabel('PENDING')).toBe('Oczekuje');
    expect(documentStatusLabel('PROCESSING')).toBe('Przetwarzanie');
    expect(documentStatusLabel('READY')).toBe('Gotowe');
    expect(documentStatusLabel('FAILED')).toBe('Błąd');
  });

  it('nieznany status zwraca sam string (fallback)', () => {
    expect(documentStatusLabel('WAT')).toBe('WAT');
  });
});

describe('documentStatusVariant', () => {
  it('mapuje statusy na warianty badge', () => {
    expect(documentStatusVariant('PENDING')).toBe('info');
    expect(documentStatusVariant('PROCESSING')).toBe('warning');
    expect(documentStatusVariant('READY')).toBe('success');
    expect(documentStatusVariant('FAILED')).toBe('error');
  });

  it('nieznany status → neutral', () => {
    expect(documentStatusVariant('WAT')).toBe('neutral');
  });
});
