(function () {
  'use strict';

  const cc = (window.cc = window.cc || {});
  const CC = (window.CC = window.CC || ({} as CcShared));
  const TX = (CC.transcript = CC.transcript || ({} as TranscriptNs));

  const el = TX.el;
  const conversationEl = TX.conversationEl;
  const rows = TX.rows;
  const toolCards = TX.toolCards;

  const MAX_ENTRIES = 500;

  function dropRow(id: unknown): HTMLElement | null {
    const rec = rows.get(id);
    if (!rec) {
      return null;
    }
    if (rec.el && rec.el.parentNode) {
      rec.el.parentNode.removeChild(rec.el);
    }
    if (rec.toolUseId) {
      toolCards.delete(rec.toolUseId);
    }
    rows.delete(id);
    return rec.el || null;
  }

  function dropOrphans(gone: HTMLElement[]): void {
    if (!gone.length) {
      return;
    }
    const orphans: unknown[] = [];
    rows.forEach(function (rec, id) {
      for (let i = 0; i < gone.length; i++) {
        if (rec.el && gone[i].contains(rec.el)) {
          orphans.push(id);
          return;
        }
      }
    });
    for (let j = 0; j < orphans.length; j++) {
      dropRow(orphans[j]);
    }
  }

  function shiftOrders(removed: number): void {
    if (removed <= 0) {
      return;
    }
    rows.forEach(function (rec) {
      if (rec.el && rec.el.__order != null) {
        rec.el.__order = Math.max(0, rec.el.__order - removed);
      }
    });
  }

  function trimNoticeText(total: number): string {
    return (
      total +
      (total === 1 ? ' earlier row was' : ' earlier rows were') +
      ' dropped to keep the transcript at ' +
      MAX_ENTRIES +
      ' rows. Nothing was lost: the session file on disk still holds the whole conversation.'
    );
  }

  function renderTrimNotice(total: number): void {
    const c = conversationEl();
    if (!c) {
      return;
    }
    const node = c.querySelector('.trim-notice');
    if (total <= 0) {
      if (node) {
        c.removeChild(node);
      }
      return;
    }
    const text = trimNoticeText(total);
    if (node) {
      node.textContent = text;
      return;
    }
    c.insertBefore(el('div', { class: 'notice trim-notice', text: text }), c.firstChild);
    if (CC.announce) {
      CC.announce(text);
    }
  }

  cc.trimRows = function (input?: unknown): void {
    const payload = input as { ids?: unknown; total?: unknown } | null | undefined;
    if (!payload) {
      return;
    }
    const ids = Array.isArray(payload.ids) ? payload.ids : [];
    const gone: HTMLElement[] = [];
    for (let i = 0; i < ids.length; i++) {
      const el = dropRow(ids[i]);
      if (el) gone.push(el);
    }
    dropOrphans(gone);
    shiftOrders(ids.length);
    const total = typeof payload.total === 'number' ? payload.total : 0;
    renderTrimNotice(total);
    TX.refreshSearch([]);
  };
})();
