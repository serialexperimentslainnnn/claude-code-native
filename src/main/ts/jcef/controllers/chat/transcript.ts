(function () {
  'use strict';

  const cc = (window.cc = window.cc || {});
  const CC = (window.CC = window.CC || ({} as CcShared));
  const TX = (CC.transcript = CC.transcript || ({} as TranscriptNs));

  const conversationEl = TX.conversationEl;
  const rows = TX.rows;
  const toolCards = TX.toolCards;

  function emptyEl(): HTMLElement | null {
    return document.getElementById('empty');
  }

  function upsert(entry: TranscriptEntry | null | undefined): RowRec | null {
    if (entry == null || entry.id == null) {
      return null;
    }

    if (entry.speaker === 'TOOL_OUTPUT') {
      if (TX.routeToolOutput(entry)) {
        return rows.get(entry.id) || null;
      }
    }

    let rec = rows.get(entry.id) || null;
    if (rec && rec.speaker !== entry.speaker) {
      if (rec.el && rec.el.parentNode) {
        rec.el.parentNode.removeChild(rec.el);
      }
      if (rec.toolUseId) {
        toolCards.delete(rec.toolUseId);
      }
      rows.delete(entry.id);
      rec = null;
    }
    if (!rec) {
      rec = TX.createRow(entry);
      rows.set(entry.id, rec);
    }
    TX.updateRow(rec, entry);
    return rec;
  }

  function containerFor(entry: TranscriptEntry): HTMLElement | null {
    if (entry.parent) {
      const parentCard = toolCards.get(entry.parent);
      if (parentCard) {
        return (
          parentCard.__childrenNode ||
          parentCard.querySelector<HTMLElement>('.tool-children') ||
          conversationEl()
        );
      }
    }
    return conversationEl();
  }

  function reposition(entry: TranscriptEntry): void {
    const rec = rows.get(entry.id);
    if (!rec || !rec.el) {
      return;
    }
    const order = entry.order;
    rec.el.__order = typeof order === 'number' && order >= 0 ? order : null;
    const container = containerFor(entry);
    if (!container) {
      return;
    }

    let ref: RowEl | null = null;
    if (rec.el.__order != null) {
      const kids = container.children;
      for (let i = 0; i < kids.length; i++) {
        const k = kids[i] as RowEl;
        if (k === rec.el) {
          continue;
        }
        if (k.__order == null) {
          continue;
        }
        if (k.__order > rec.el.__order) {
          ref = k;
          break;
        }
      }
    }
    if (rec.el.parentNode === container && rec.el.nextSibling === ref) {
      return;
    }
    if (ref) {
      container.insertBefore(rec.el, ref);
    } else {
      container.appendChild(rec.el);
    }
  }

  function hasRows(c: HTMLElement): boolean {
    for (let i = 0; i < c.children.length; i++) {
      if (c.children[i].id !== 'empty') return true;
    }
    return false;
  }

  function showEmptyState(show: boolean): void {
    const empty = emptyEl();
    if (empty) {
      empty.hidden = !show;
    }
  }

  function entryFailed(entry: TranscriptEntry | null | undefined, error: unknown): void {
    CC.reportError('transcript entry ' + (entry && entry.id != null ? entry.id : '?'), error);
  }

  cc.batch = function (input?: unknown): void {
    if (!Array.isArray(input)) {
      return;
    }
    const entries = input as TranscriptEntry[];
    const c = conversationEl();
    const stick = TX.stickToBottom();
    const touched: RowRec[] = [];

    for (let i = 0; i < entries.length; i++) {
      try {
        const rec = upsert(entries[i]);
        if (rec) touched.push(rec);
      } catch (e) {
        entryFailed(entries[i], e);
      }
    }
    for (let j = 0; j < entries.length; j++) {
      const e = entries[j];
      if (!e || e.id == null || (e.speaker === 'TOOL_OUTPUT' && !rows.has(e.id))) continue;
      try {
        reposition(e);
      } catch (err) {
        entryFailed(e, err);
      }
    }

    if (rows.size > 0 || (c && hasRows(c))) {
      showEmptyState(false);
    }

    TX.refreshSearch(touched);
    TX.setStreaming(anyStreaming());
    TX.scheduleScroll(stick);
  };

  function anyStreaming(): boolean {
    let found = false;
    rows.forEach(function (rec) {
      if (rec.stream) found = true;
    });
    return found;
  }

  const streaming = new Set<RowRec>();
  let frameAsked = false;

  function nextFrame(fn: () => void): void {
    if (typeof window.requestAnimationFrame === 'function') window.requestAnimationFrame(fn);
    else setTimeout(fn, 16);
  }

  function flushStreaming(): void {
    frameAsked = false;
    const recs = Array.from(streaming);
    streaming.clear();
    const stick = TX.stickToBottom();
    for (let i = 0; i < recs.length; i++) {
      if (recs[i].state === 'RUNNING') TX.setBody(recs[i], recs[i].text, true);
    }
    TX.refreshSearch(recs);
    TX.setStreaming(anyStreaming());
    TX.scheduleScroll(stick);
  }

  cc.append = function (input?: unknown): void {
    const payload = input as { id?: unknown; delta?: unknown } | null | undefined;
    if (!payload || payload.id == null || typeof payload.delta !== 'string') {
      return;
    }
    const rec = rows.get(payload.id);
    if (!rec || rec.kind !== 'md' || rec.state !== 'RUNNING') {
      return;
    }
    rec.text = (rec.text || '') + payload.delta;
    rec.bodyKey = 'x:' + rec.text;
    streaming.add(rec);
    if (!frameAsked) {
      frameAsked = true;
      nextFrame(flushStreaming);
    }
  };

  cc.clear = function (): void {
    rows.clear();
    toolCards.clear();
    const c = conversationEl();
    if (c) {
      const kids = Array.prototype.slice.call(c.children) as Element[];
      for (let i = 0; i < kids.length; i++) {
        if (kids[i].id === 'empty') {
          continue;
        }
        c.removeChild(kids[i]);
      }
    }
    TX.resetSearch();
    TX.setStreaming(false);
    showEmptyState(true);
  };
})();
