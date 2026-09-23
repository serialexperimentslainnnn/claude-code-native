(function () {
  'use strict';

  const cc = (window.cc = window.cc || {});
  const CC = (window.CC = window.CC || ({} as CcShared));
  const TX = (CC.transcript = CC.transcript || ({} as TranscriptNs));

  const el = TX.el;
  const safeSend = TX.safeSend;
  const rows = TX.rows;

  const PATH_RE = new RegExp(
    '(?:' +
      '(?:~\\/|\\.{1,2}\\/|\\/)[\\w.-]+(?:\\/[\\w.-]+)*\\/?' +
      '|' +
      '[\\w.-]+\\/(?:[\\w.-]+\\/?)*' +
      '|' +
      '[\\w.-]+\\.[A-Za-z][\\w]{0,9}' +
      ')(?::\\d+)?',
    'g'
  );
  const SYMBOL_RE = /\b([A-Z][A-Za-z0-9]{2,}|[a-z][A-Za-z0-9]{2,}(?=\(\)))\b/g;

  function insideLinkOrPre(n: Node, root: Node): boolean {
    let p: Node | null = n.parentNode;
    while (p && p !== root) {
      const t = (p as Element).tagName;
      if (t === 'A' || t === 'PRE') {
        return true;
      }
      p = p.parentNode;
    }
    return false;
  }

  function collectCandidates(root: Node): { paths: string[]; symbols: string[] } {
    const paths: Record<string, true> = {};
    const symbols: Record<string, true> = {};
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: function (n: Node) {
        return insideLinkOrPre(n, root) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT;
      },
    });
    let n: Node | null;
    let m: RegExpExecArray | null;
    while ((n = walker.nextNode())) {
      const txt = n.nodeValue || '';
      PATH_RE.lastIndex = 0;
      while ((m = PATH_RE.exec(txt))) {
        paths[m[0]] = true;
      }
      const inCode = !!n.parentNode && (n.parentNode as Element).tagName === 'CODE';
      if (inCode) {
        SYMBOL_RE.lastIndex = 0;
        while ((m = SYMBOL_RE.exec(txt))) {
          symbols[m[1]] = true;
        }
      }
    }
    return { paths: Object.keys(paths), symbols: Object.keys(symbols) };
  }

  TX.requestLinks = function (rec: RowRec, entry: TranscriptEntry): void {
    if (!rec || !rec.bodyNode) {
      return;
    }
    TX.unmark(rec.bodyNode);
    const c = collectCandidates(rec.bodyNode);
    if (!c.paths.length && !c.symbols.length) {
      return;
    }
    safeSend({ type: 'resolveLinks', rowId: entry.id, paths: c.paths, symbols: c.symbols });
  };

  function applyLinks(input: unknown): void {
    const payload = input as { rowId?: unknown; links?: unknown } | null | undefined;
    if (!payload || payload.rowId == null) {
      return;
    }
    const rec = rows.get(payload.rowId);
    if (!rec || !rec.bodyNode) {
      return;
    }
    const links: LinkHit[] = Array.isArray(payload.links) ? (payload.links as LinkHit[]) : [];
    const byToken = new Map<string, LinkHit>();
    for (let i = 0; i < links.length; i++) {
      const token = String((links[i] && links[i].token) || '');
      if (token && !byToken.has(token)) byToken.set(token, links[i]);
    }
    if (!byToken.size) {
      return;
    }
    TX.unmark(rec.bodyNode);
    linkifyAll(rec.bodyNode, byToken);
    TX.remark(rec.bodyNode);
  }

  const TOKEN_LEFT = /[\w.\-/~]/;
  const TOKEN_RIGHT = /[\w.\-/]/;
  function atTokenBoundary(txt: string, at: number, token: string): boolean {
    if (at > 0 && TOKEN_LEFT.test(txt.charAt(at - 1))) {
      return false;
    }
    const after = txt.charAt(at + token.length);
    return !(after && TOKEN_RIGHT.test(after));
  }

  function anchorFor(token: string, link: LinkHit): HTMLElement {
    return el('a', {
      class: 'jb-link',
      text: token,
      attrs: {
        href: TX.jbHref(link.path, link.line),
        title: 'Open ' + link.path + (link.line ? ':' + link.line : ''),
      },
    });
  }

  function tokenAt(txt: string, at: number, tokens: string[]): string | null {
    for (let i = 0; i < tokens.length; i++) {
      if (txt.startsWith(tokens[i], at) && atTokenBoundary(txt, at, tokens[i])) return tokens[i];
    }
    return null;
  }

  function linkifyNode(node: Node, finder: RegExp, tokens: string[], byToken: Map<string, LinkHit>): void {
    const txt = node.nodeValue || '';
    let frag: DocumentFragment | null = null;
    let from = 0;
    finder.lastIndex = 0;
    let m: RegExpExecArray | null;
    while ((m = finder.exec(txt))) {
      const token = tokenAt(txt, m.index, tokens);
      if (!token) {
        finder.lastIndex = m.index + 1;
        continue;
      }
      frag = frag || document.createDocumentFragment();
      frag.appendChild(document.createTextNode(txt.slice(from, m.index)));
      frag.appendChild(anchorFor(token, byToken.get(token) as LinkHit));
      from = m.index + token.length;
      finder.lastIndex = from;
    }
    if (!frag || !node.parentNode) {
      return;
    }
    frag.appendChild(document.createTextNode(txt.slice(from)));
    node.parentNode.replaceChild(frag, node);
  }

  function escapeRe(s: string): string {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  function linkifyAll(root: Node, byToken: Map<string, LinkHit>): void {
    const tokens = Array.from(byToken.keys()).sort(function (a, b) {
      return b.length - a.length;
    });
    const finder = new RegExp(tokens.map(escapeRe).join('|'), 'g');
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: function (n: Node) {
        return insideLinkOrPre(n, root) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT;
      },
    });
    const targets: Node[] = [];
    let n: Node | null;
    while ((n = walker.nextNode())) {
      targets.push(n);
    }
    for (let i = 0; i < targets.length; i++) {
      linkifyNode(targets[i], finder, tokens, byToken);
    }
  }

  cc.links = function (payload?: unknown): void {
    try {
      applyLinks(payload);
    } catch (e) {
      CC.reportError('links', e);
    }
  };
})();
