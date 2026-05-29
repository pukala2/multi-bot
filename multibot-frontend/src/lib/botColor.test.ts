import { describe, it, expect } from 'vitest';
import { botColor, botColorTint } from './botColor';

const PALETTE = [
  '#7B5BFF', '#60A5FA', '#4ADE80', '#FBBF24',
  '#F472B6', '#2DD4BF', '#FB923C', '#A78BFA',
];

describe('botColor', () => {
  it('jest deterministyczny — ten sam seed → ten sam kolor', () => {
    const seed = '9f8e7d6c-1234-4abc-9def-000000000001';
    expect(botColor(seed)).toBe(botColor(seed));
  });

  it('zawsze zwraca kolor z palety', () => {
    for (const seed of ['a', 'bot-1', 'organizacja', '', 'xyz123']) {
      expect(PALETTE).toContain(botColor(seed));
    }
  });

  it('różne seedy zwykle dają różne kolory', () => {
    const colors = ['bot-1', 'bot-2', 'bot-3', 'bot-4'].map(botColor);
    expect(new Set(colors).size).toBeGreaterThan(1);
  });
});

describe('botColorTint', () => {
  it('dokleja alfę 22 do koloru bazowego (RRGGBBAA)', () => {
    const seed = 'bot-1';
    expect(botColorTint(seed)).toBe(`${botColor(seed)}22`);
  });
});
