import { describe, it, expect } from 'vitest';
import { formatBytes, shortUuid, plPlural } from './format';

describe('formatBytes', () => {
  it('zwraca 0 B dla zera', () => {
    expect(formatBytes(0)).toBe('0 B');
  });

  it('bajty bez miejsc po przecinku', () => {
    expect(formatBytes(512)).toBe('512 B');
  });

  it('skaluje do KB/MB/GB z jednym miejscem', () => {
    expect(formatBytes(1024)).toBe('1.0 KB');
    expect(formatBytes(1536)).toBe('1.5 KB');
    expect(formatBytes(1024 * 1024)).toBe('1.0 MB');
    expect(formatBytes(1024 * 1024 * 1024)).toBe('1.0 GB');
  });
});

describe('shortUuid', () => {
  it('skraca długi uuid z wielokropkiem', () => {
    const uuid = '123e4567-e89b-12d3-a456-426614174000';
    expect(shortUuid(uuid)).toBe('123e4567…4000');
  });

  it('zwraca krótki ciąg bez zmian', () => {
    expect(shortUuid('abc')).toBe('abc');
  });

  it('respektuje własne head/tail', () => {
    expect(shortUuid('123e4567-e89b-12d3-a456-426614174000', 4, 2)).toBe('123e…00');
  });
});

describe('plPlural — polska odmiana liczebnika', () => {
  const forms = ['rozmowa', 'rozmowy', 'rozmów'] as const;

  it('1 → forma pojedyncza', () => {
    expect(plPlural(1, forms)).toBe('rozmowa');
  });

  it('2–4 → forma mnoga (mianownik lm)', () => {
    expect(plPlural(2, forms)).toBe('rozmowy');
    expect(plPlural(3, forms)).toBe('rozmowy');
    expect(plPlural(4, forms)).toBe('rozmowy');
  });

  it('0 oraz 5–21 → dopełniacz lm', () => {
    expect(plPlural(0, forms)).toBe('rozmów');
    expect(plPlural(5, forms)).toBe('rozmów');
    expect(plPlural(11, forms)).toBe('rozmów');
    expect(plPlural(21, forms)).toBe('rozmów');
  });

  it('wyjątek 12–14 → dopełniacz lm (nie mnoga)', () => {
    expect(plPlural(12, forms)).toBe('rozmów');
    expect(plPlural(13, forms)).toBe('rozmów');
    expect(plPlural(14, forms)).toBe('rozmów');
  });

  it('22–24 → forma mnoga (powtórka reguły dla setek)', () => {
    expect(plPlural(22, forms)).toBe('rozmowy');
    expect(plPlural(24, forms)).toBe('rozmowy');
    expect(plPlural(112, forms)).toBe('rozmów');
    expect(plPlural(122, forms)).toBe('rozmowy');
  });
});
