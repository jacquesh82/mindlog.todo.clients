import Shepherd from 'shepherd.js';
import 'shepherd.js/dist/css/shepherd.css';

// First-login guided tour (Shepherd.js). Anchored to [data-tour="…"] elements
// in the sidebar. Progress + opt-out are persisted in localStorage.

const DONE = 'mindlog_tour_done';
const DISABLED = 'mindlog_tour_disabled';

export function isTourDisabled(): boolean {
  return localStorage.getItem(DISABLED) === '1';
}
export function setTourDisabled(disabled: boolean): void {
  if (disabled) localStorage.setItem(DISABLED, '1');
  else localStorage.removeItem(DISABLED);
}
function isDone(): boolean {
  return localStorage.getItem(DONE) === '1';
}
function markDone(): void {
  localStorage.setItem(DONE, '1');
}

type Translate = (key: string) => string;

/** Start the tour on first login (unless already seen or disabled). */
export function maybeStartTour(t: Translate): void {
  if (isDone() || isTourDisabled()) return;
  // Defer so the sidebar has mounted and the anchors exist.
  setTimeout(() => startTour(t), 400);
}

/**
 * Is the anchor actually ON SCREEN?
 *
 * Below `md` the sidebar is a `fixed` off-canvas drawer (it only turns `static`
 * from 768px up), so its `[data-tour]` elements still EXIST — with a rectangle
 * sitting outside the viewport. Shepherd faithfully pins the bubble next to it
 * and the step lands off-screen. An unanchored step is centred instead, which
 * is what a drawer-based layout wants anyway.
 */
function anchorOnScreen(selector: string): boolean {
  const el = document.querySelector(selector);
  if (!el) return false;
  const r = el.getBoundingClientRect();
  if (r.width === 0 || r.height === 0) return false;
  // Horizontal only: vertical overflow is handled by `scrollTo`, off-canvas is not.
  return r.right > 0 && r.left < window.innerWidth;
}

/** Start (or replay) the guided tour. */
export function startTour(t: Translate): void {
  // Le tiroir est fermé sous 768px (cf. Sidebar.tsx) : aucune ancre n'y est
  // atteignable, on centre toutes les étapes.
  const narrow = window.matchMedia('(max-width: 767px)').matches;
  const tour = new Shepherd.Tour({
    useModalOverlay: true,
    defaultStepOptions: {
      scrollTo: true,
      cancelIcon: { enabled: true },
      classes: 'mindlog-shepherd',
    },
  });

  const buttons = (last = false) => [
    ...(last
      ? []
      : [{ text: t('tour.skip'), action: () => tour.cancel(), classes: 'shepherd-button-secondary' }]),
    {
      text: last ? t('tour.done') : t('tour.next'),
      action: () => (last ? tour.complete() : tour.next()),
    },
  ];

  const steps: Array<{ id: string; el: string; on: 'top' | 'bottom' | 'right'; last?: boolean }> = [
    { id: 'welcome', el: '[data-tour="welcome"]', on: 'bottom' },
    { id: 'search', el: '[data-tour="search"]', on: 'right' },
    { id: 'notes', el: '[data-tour="notes"]', on: 'right' },
    { id: 'dashboard', el: '[data-tour="dashboard"]', on: 'right' },
    { id: 'settings', el: '[data-tour="settings"]', on: 'top', last: true },
  ];

  for (const s of steps) {
    const anchored = !narrow && anchorOnScreen(s.el);
    tour.addStep({
      id: s.id,
      title: t(`tour.${s.id}.title`),
      text: t(`tour.${s.id}.text`),
      ...(anchored ? { attachTo: { element: s.el, on: s.on } } : {}),
      buttons: buttons(s.last),
    });
  }

  tour.on('complete', markDone);
  tour.on('cancel', markDone);
  tour.start();
}
