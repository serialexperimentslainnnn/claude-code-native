(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const TX = (CC.transcript = CC.transcript || ({} as TranscriptNs));

  const safeSend = TX.safeSend;
  const conversationEl = TX.conversationEl;
  const rows = TX.rows;

  let currentQuery = '';
  let searchHits: HTMLElement[] = [];
  let activeIndex = 0;

  function refreshCount(): void {
    if (typeof TX.updateFindCount === 'function') TX.updateFindCount();
  }

  function setActiveHit(i: number, scroll: boolean): void {
    if (!searchHits.length) {
      activeIndex = 0;
      refreshCount();
      return;
    }
    const n = searchHits.length;
    activeIndex = ((i % n) + n) % n;
    for (let k = 0; k < n; k++) searchHits[k].classList.remove('active');
    const hit = searchHits[activeIndex];
    hit.classList.add('active');
    if (scroll) {
      try {
        hit.scrollIntoView({ block: 'center', inline: 'nearest' });
      } catch (e) {}
    }
    refreshCount();
  }
  TX.findNext = function (): void {
    setActiveHit(activeIndex + 1, true);
  };
  TX.findPrev = function (): void {
    setActiveHit(activeIndex - 1, true);
  };
  TX.hitCount = function (): number {
    return searchHits.length;
  };
  TX.activeHit = function (): number {
    return activeIndex;
  };

  function unmark(root: ParentNode | null): void {
    if (!root) {
      return;
    }
    const marks = root.querySelectorAll('mark.cc-hit');
    const parents = new Set<Node>();
    for (let i = 0; i < marks.length; i++) {
      const m = marks[i];
      const parent = m.parentNode;
      if (!parent) {
        continue;
      }
      parent.replaceChild(document.createTextNode(m.textContent || ''), m);
      parents.add(parent);
    }
    parents.forEach(function (parent) {
      parent.normalize();
    });
  }
  TX.unmark = unmark;

  function clearHighlights(): void {
    unmark(conversationEl());
    searchHits = [];
  }

  function collectHits(): void {
    const c = conversationEl();
    searchHits = c ? Array.prototype.slice.call(c.querySelectorAll('mark.cc-hit')) : [];
  }

  TX.remark = function (root: Node | null): void {
    if (!currentQuery || !root) {
      return;
    }
    highlightInNode(root, currentQuery.toLowerCase());
    collectHits();
    setActiveHit(Math.min(activeIndex, Math.max(searchHits.length - 1, 0)), false);
  };

  function highlightInNode(node: Node, lower: string): number {
    let count = 0;
    const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT, null);
    const textNodes: Text[] = [];
    let n: Node | null;
    while ((n = walker.nextNode())) {
      if (n.nodeValue && n.nodeValue.length) {
        textNodes.push(n as Text);
      }
    }
    for (let i = 0; i < textNodes.length; i++) {
      const tn = textNodes[i];
      const val = tn.nodeValue || '';
      const hay = val.toLowerCase();
      if (hay.indexOf(lower) === -1) {
        continue;
      }
      const frag = document.createDocumentFragment();
      let idx = 0;
      let pos: number;
      while ((pos = hay.indexOf(lower, idx)) !== -1) {
        if (pos > idx) {
          frag.appendChild(document.createTextNode(val.slice(idx, pos)));
        }
        const mark = document.createElement('mark');
        mark.className = 'cc-hit';
        mark.textContent = val.slice(pos, pos + lower.length);
        frag.appendChild(mark);
        searchHits.push(mark);
        count++;
        idx = pos + lower.length;
      }
      if (idx < val.length) {
        frag.appendChild(document.createTextNode(val.slice(idx)));
      }
      if (tn.parentNode) {
        tn.parentNode.replaceChild(frag, tn);
      }
    }
    return count;
  }

  function runSearch(q: string | null | undefined, silent: boolean): void {
    clearHighlights();
    currentQuery = q || '';
    if (!currentQuery) {
      if (!silent) {
        safeSend({ type: 'search', count: 0 });
      }
      return;
    }
    const lower = currentQuery.toLowerCase();
    let total = 0;
    rows.forEach(function (rec) {
      if (!rec || !rec.bodyNode) {
        return;
      }
      total += highlightInNode(rec.bodyNode, lower);
    });
    collectHits();
    if (searchHits.length) {
      if (silent) {
        setActiveHit(Math.min(activeIndex, searchHits.length - 1), false);
      } else {
        setActiveHit(0, true);
      }
    }
    if (!silent) {
      safeSend({ type: 'search', count: total });
    }
  }
  TX.runSearch = runSearch;

  TX.refreshSearch = function (recs?: RowRec[]): void {
    if (!currentQuery) {
      return;
    }
    if (!recs) {
      runSearch(currentQuery, true);
      return;
    }
    const lower = currentQuery.toLowerCase();
    for (let i = 0; i < recs.length; i++) {
      const body = recs[i] && recs[i].bodyNode;
      if (!body) continue;
      unmark(body);
      highlightInNode(body, lower);
    }
    collectHits();
    setActiveHit(Math.min(activeIndex, Math.max(searchHits.length - 1, 0)), false);
  };

  TX.resetSearch = function (): void {
    currentQuery = '';
    searchHits = [];
    activeIndex = 0;
    if (typeof TX.resetFindBar === 'function') TX.resetFindBar();
  };

  function subscribe(): boolean {
    if (!CC.on) {
      return false;
    }
    CC.on('search', function (q) {
      TX.runSearch(q as string, false);
      refreshCount();
    });
    return true;
  }

  if (!subscribe()) {
    let tries = 0;
    const iv = setInterval(function () {
      tries++;
      if (subscribe() || tries > 50) {
        clearInterval(iv);
      }
    }, 20);
  }
})();
