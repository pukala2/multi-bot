// Deterministyczny kolor bota z jego id — ten sam bot zawsze ten sam kolor,
// żeby łatwo rozróżniać boty w przełączniku/listach.
const BOT_COLORS = [
  '#7B5BFF', // fiolet (accent)
  '#60A5FA', // niebieski
  '#4ADE80', // zielony
  '#FBBF24', // bursztyn
  '#F472B6', // róż
  '#2DD4BF', // turkus
  '#FB923C', // pomarańcz
  '#A78BFA', // lawenda
];

export function botColor(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i++) {
    h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  }
  return BOT_COLORS[h % BOT_COLORS.length];
}

/** Tło ikonki = kolor z niską przezroczystością (8-cyfrowy hex RRGGBBAA). */
export function botColorTint(seed: string): string {
  return `${botColor(seed)}22`;
}
