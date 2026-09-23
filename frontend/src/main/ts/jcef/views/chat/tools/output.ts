(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const TX = (CC.transcript = CC.transcript || ({} as TranscriptNs));

  const el = TX.el;
  const toolCards = TX.toolCards;

  const MAX_JSON_CHARS = 200000;

  function prettyJson(text: string): string | null {
    if (!text || text.length > MAX_JSON_CHARS) {
      return null;
    }
    const s = text.trim();
    const head = s.charAt(0);
    const tail = s.charAt(s.length - 1);
    if ((head !== '{' || tail !== '}') && (head !== '[' || tail !== ']')) {
      return null;
    }
    try {
      return JSON.stringify(JSON.parse(s), null, 2);
    } catch (e) {
      return null;
    }
  }

  function decorate(codeEl: HTMLElement, language: string | null): void {
    if (typeof CC.decorateOneCodeBlock !== 'function') return;
    if (language) codeEl.className = 'language-' + language;
    CC.decorateOneCodeBlock(codeEl);
  }

  TX.routeToolOutput = function (entry: TranscriptEntry, cards?: Map<string, RowEl>): boolean {
    const known = cards || toolCards;
    const tid = entry.toolUseId;
    if (!tid) {
      return false;
    }
    const card = known.get(tid);
    if (!card) {
      return false;
    }
    const out = card.__outNode || card.querySelector<HTMLElement>('.tool-out');
    if (!out) {
      return false;
    }
    const pid = 'to-' + entry.id;
    let block = blockFor(out, pid);
    const raw = entry.text == null ? '' : String(entry.text);
    const key = (entry.meta || '') + '\u0000' + (card.__filePath || '') + '\u0000' + textKey(raw);
    if (block && block.__outKey === key) {
      return true;
    }
    if (entry.meta === 'toon') {
      if (!block) {
        block = el('div', { class: 'toon' });
        block.setAttribute('data-out-id', pid);
        out.appendChild(block);
      }
      const value = TX.renderToon(block, raw);
      block.__outKey = key;
      setFoot(card, foot(value));
      return true;
    }
    if (!block) {
      block = el('pre', {});
      block.setAttribute('data-out-id', pid);
      block.appendChild(el('code', {}));
      out.appendChild(block);
    }
    block.__outKey = key;
    if (entry.meta !== 'live') block.__liveText = undefined;
    const codeEl = block.querySelector<HTMLElement>('code');
    if (codeEl) {
      const tags = ' ' + (entry.meta || '') + ' ';
      const fileLang = typeof CC.languageForPath === 'function' ? CC.languageForPath(card.__filePath) : null;
      if (entry.meta === 'diff') {
        renderDiff(codeEl, raw, fileLang);
        block.classList.add('diff');
        block.classList.remove('command');
        block.classList.remove('flow');
      } else if (tags.indexOf(' command ') >= 0) {
        block.classList.remove('diff');
        block.classList.remove('flow');
        block.classList.add('command');
        codeEl.textContent = raw;
        if (typeof CC.decorateOneCodeBlock === 'function') {
          CC.decorateOneCodeBlock(codeEl);
          const langLabel = block.querySelector('.code-lang');
          if (langLabel) {
            langLabel.textContent = 'shell';
          }
        }
      } else if (entry.meta === 'live') {
        block.classList.remove('diff');
        block.classList.remove('command');
        block.classList.add('flow');
        block.classList.add('live');
        const shown = block.__liveText;
        if (shown && raw.length > shown.length && raw.startsWith(shown)) {
          codeEl.appendChild(document.createTextNode(raw.slice(shown.length)));
        } else {
          codeEl.textContent = raw;
        }
        block.__liveText = raw;
        codeEl.scrollTop = codeEl.scrollHeight;
        setLiveTail(card, raw);
      } else {
        block.classList.remove('diff');
        block.classList.remove('command');
        const json = prettyJson(raw);
        codeEl.textContent = json == null ? raw : json;
        if (card.__filePath) {
          block.classList.remove('flow');
          decorate(codeEl, fileLang);
        } else {
          block.classList.add('flow');
          if (json != null) decorate(codeEl, 'json');
        }
      }
    }
    return true;
  };

  function tailRow(card: RowEl): HTMLElement {
    let tail = card.__liveTail || null;
    if (!tail) {
      tail = el('div', { class: 'tool-live-tail' });
      tail.appendChild(el('span', { class: 'tool-foot' }));
      tail.appendChild(el('span', { class: 'tool-last' }));
      const out = card.__outNode || card.querySelector<HTMLElement>('.tool-out');
      if (out && out.parentNode) out.parentNode.insertBefore(tail, out);
      else card.appendChild(tail);
      card.__liveTail = tail;
    }
    return tail;
  }

  function setTailPart(card: RowEl, part: string, text: string): void {
    const tail = tailRow(card);
    const span = tail.querySelector<HTMLElement>('.' + part);
    if (span) span.textContent = text;
    tail.hidden = !tail.textContent;
  }

  function setLiveTail(card: RowEl, raw: string): void {
    const lines = raw.split('\n');
    let last = '';
    for (let i = lines.length - 1; i >= 0 && !last; i--) last = lines[i].trim();
    setTailPart(card, 'tool-last', last);
  }

  function setFoot(card: RowEl, text: string): void {
    if (text || card.__liveTail) setTailPart(card, 'tool-foot', text);
  }

  const FOOT_KEYS = [
    'status',
    'exit_code',
    'passed',
    'failed',
    'ignored',
    'errors_count',
    'warnings_count',
    'lines',
  ];

  function foot(value: unknown): string {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return '';
    const row = value as Record<string, unknown>;
    const parts: string[] = [];
    if (Array.isArray(row.items) && typeof row.count === 'number') parts.push(row.count + ' items');
    FOOT_KEYS.forEach(function (key) {
      const v = row[key];
      if (v == null || v === '' || (key !== 'status' && typeof v !== 'number')) return;
      if (key === 'status') parts.push(String(v));
      else if (key === 'exit_code') parts.push('exit ' + v);
      else parts.push(v + ' ' + key.replace('_count', '').replace('_', ' '));
    });
    return parts.join(' · ');
  }

  TX.scrollLiveToEnd = function (card: HTMLElement): void {
    const codes = card.querySelectorAll<HTMLElement>('.tool-out pre.live code');
    for (let i = 0; i < codes.length; i++) codes[i].scrollTop = codes[i].scrollHeight;
  };

  function blockFor(out: HTMLElement, pid: string): OutBlock | null {
    for (let i = 0; i < out.children.length; i++) {
      if (out.children[i].getAttribute('data-out-id') === pid) return out.children[i] as OutBlock;
    }
    return null;
  }

  function textKey(s: string): string {
    let hash = 2166136261;
    for (let i = 0; i < s.length; i++) {
      hash = Math.imul(hash ^ s.charCodeAt(i), 16777619);
    }
    return s.length + ':' + (hash >>> 0).toString(36);
  }

  function lineClass(line: string): string {
    if (line.indexOf('@@') === 0) return 'dl-hunk';
    const c0 = line.charAt(0);
    return c0 === '+' ? 'dl-add' : c0 === '-' ? 'dl-del' : 'dl-ctx';
  }

  function sideLines(lines: string[], skip: string, lang: string): string[] | null {
    const picked: string[] = [];
    for (let i = 0; i < lines.length; i++) {
      const cls = lineClass(lines[i]);
      if (cls !== 'dl-hunk' && cls !== skip) picked.push(lines[i].slice(1));
    }
    const html = CC.highlight(picked.join('\n'), lang);
    return html === null ? null : CC.highlightLines(html);
  }

  function renderDiff(codeEl: HTMLElement, text: string, lang: string | null): void {
    codeEl.innerHTML = '';
    const lines = String(text).split('\n');
    const before = lang ? sideLines(lines, 'dl-add', lang) : null;
    const after = lang ? sideLines(lines, 'dl-del', lang) : null;
    let b = 0;
    let a = 0;
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const cls = lineClass(line);
      const span = el('span', { class: 'diff-line ' + cls });
      const trailingNl = i < lines.length - 1 ? '\n' : '';
      let html: string | undefined;
      if (cls === 'dl-del') html = before ? before[b++] : undefined;
      else if (cls !== 'dl-hunk') {
        html = after ? after[a++] : undefined;
        if (cls === 'dl-ctx') b++;
      }
      if (html !== undefined && line.length > 0) {
        span.appendChild(document.createTextNode(line.charAt(0)));
        span.insertAdjacentHTML('beforeend', html);
        if (trailingNl) span.appendChild(document.createTextNode(trailingNl));
      } else {
        span.textContent = line + trailingNl;
      }
      codeEl.appendChild(span);
    }
  }
})();
