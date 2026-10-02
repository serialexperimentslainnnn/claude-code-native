(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const D = (CC.dash = CC.dash || ({} as DashNs));

  const FOCUSABLE = 'a[href], button, input, select, textarea, summary, [tabindex]';

  function keyOf(node: Element | null | undefined): string | null {
    return node && node.getAttribute ? node.getAttribute('data-card') : null;
  }

  function focusKey(el: Element): string {
    return (
      el.tagName +
      '|' +
      (el.getAttribute('data-key') || el.getAttribute('aria-label') || el.textContent || '').trim()
    );
  }

  interface FocusMark {
    card: string;
    key: string;
    index: number;
  }

  function markFocus(container: HTMLElement): FocusMark | null {
    const active = document.activeElement;
    if (!active || active === document.body || !container.contains(active)) return null;
    let card: Element | null = active;
    while (card && card.parentElement !== container) card = card.parentElement;
    const id = keyOf(card);
    if (!card || id == null) return null;
    const all = Array.prototype.slice.call(card.querySelectorAll(FOCUSABLE)) as Element[];
    return { card: id, key: focusKey(active), index: all.indexOf(active) };
  }

  function restoreFocus(container: HTMLElement, mark: FocusMark): void {
    if (document.activeElement && container.contains(document.activeElement)) return;
    let card: Element | null = null;
    for (let i = 0; i < container.children.length; i++) {
      if (keyOf(container.children[i]) === mark.card) card = container.children[i];
    }
    if (!card) return;
    const all = Array.prototype.slice.call(card.querySelectorAll(FOCUSABLE)) as HTMLElement[];
    let target: HTMLElement | null = null;
    for (let j = 0; j < all.length && !target; j++) {
      if (focusKey(all[j]) === mark.key) target = all[j];
    }
    target = target || all[mark.index] || null;
    if (target) target.focus({ preventScroll: true });
  }

  function same(previous: Element, next: Element): boolean {
    const sig = next.getAttribute('data-sig');
    return sig != null ? previous.getAttribute('data-sig') === sig : previous.isEqualNode(next);
  }

  D.reconcile = function (container: HTMLElement, cards: HTMLElement[]): void {
    const focus = markFocus(container);
    const existing: Record<string, Element> = Object.create(null);
    let i: number;
    for (i = 0; i < container.children.length; i++) {
      const key = keyOf(container.children[i]);
      if (key != null) existing[key] = container.children[i];
    }

    const ordered: Element[] = [];
    for (i = 0; i < cards.length; i++) {
      const next = cards[i];
      const key = keyOf(next);
      const previous = key != null ? existing[key] : undefined;
      ordered.push(previous && same(previous, next) ? previous : next);
    }

    for (i = container.children.length - 1; i >= 0; i--) {
      if (ordered.indexOf(container.children[i]) < 0) container.removeChild(container.children[i]);
    }

    for (i = 0; i < ordered.length; i++) {
      if (container.children[i] !== ordered[i])
        container.insertBefore(ordered[i], container.children[i] || null);
    }

    if (focus) restoreFocus(container, focus);
  };
})();
