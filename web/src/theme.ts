export type Theme = 'light' | 'dark';

const KEY = 'mindlog_theme';

/** Stored preference, else the OS preference, else light. */
export function getInitialTheme(): Theme {
  const stored = localStorage.getItem(KEY);
  if (stored === 'light' || stored === 'dark') return stored;
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

/** Apply a theme to the document and persist it. */
export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme;
  localStorage.setItem(KEY, theme);
}

// --- Accent (brand) color ---

const ACCENT_KEY = 'mindlog_accent';

/**
 * The todo amber — hue 36, teinte 2 de l'échelle de la suite, la même que la
 * vitrine et que la pastille todo du rail. Choisir ce préréglage RETIRE les
 * surcharges plutôt que de poser cet hex : la feuille de style a une valeur par
 * thème (#8f621e en clair, où l'ambre vif ne passerait pas le contraste).
 */
export const DEFAULT_ACCENT = '#edba6e';
export const ACCENT_PRESETS = [
  '#edba6e', // ambre todo — teinte du produit
  '#db4c3f', // rouge mindlog — l'ancien défaut, gardé comme option
  '#e8833a', // orange
  '#2fa36b', // vert
  '#14b8a6', // turquoise
  '#4c7cf3', // bleu
  '#8b5cf6', // violet
  '#db4c8f', // rose
];

/** Luminance relative WCAG d'une couleur `#rrggbb`. */
function luminance(hex: string): number {
  const m = /^#?([\da-f]{6})$/i.exec(hex.trim());
  if (!m?.[1]) return 0;
  const n = parseInt(m[1], 16);
  const chan = (v: number): number => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * chan((n >> 16) & 255) + 0.7152 * chan((n >> 8) & 255) + 0.0722 * chan(n & 255);
}

/** Stored accent, or the default. */
export function getInitialAccent(): string {
  return localStorage.getItem(ACCENT_KEY) ?? DEFAULT_ACCENT;
}

/**
 * Apply an accent color by overriding the brand CSS variables. Hover/soft are
 * derived with color-mix so they adapt in both light and dark themes. Passing
 * the default clears the overrides (falls back to the stylesheet tokens).
 *
 * L'encre est recalculée à chaque accent : un accent clair (l'ambre, le jaune)
 * avec du texte blanc était illisible. On tranche sur la luminance — le seuil
 * 0,2 est le point où le blanc et le brun sombre donnent le même contraste.
 */
export function applyAccent(color: string): void {
  const root = document.documentElement;
  if (!color || color.toLowerCase() === DEFAULT_ACCENT) {
    root.style.removeProperty('--color-brand');
    root.style.removeProperty('--color-brand-hover');
    root.style.removeProperty('--color-brand-soft');
    root.style.removeProperty('--color-brand-ink');
    localStorage.removeItem(ACCENT_KEY);
    return;
  }
  root.style.setProperty('--color-brand', color);
  root.style.setProperty('--color-brand-hover', `color-mix(in srgb, ${color} 85%, black)`);
  root.style.setProperty('--color-brand-soft', `color-mix(in srgb, ${color} 14%, transparent)`);
  root.style.setProperty('--color-brand-ink', luminance(color) > 0.2 ? '#241a0a' : '#ffffff');
  localStorage.setItem(ACCENT_KEY, color);
}
