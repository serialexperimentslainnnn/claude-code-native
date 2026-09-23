(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const TX = (CC.transcript = CC.transcript || ({} as TranscriptNs));

  function el(tag: string, props?: HProps | null): HTMLElement {
    if (CC.h) {
      return CC.h(tag, props);
    }
    const node = document.createElement(tag);
    const p = props || {};
    if (p.class) {
      node.className = String(p.class);
    }
    if (p.text != null) {
      node.textContent = String(p.text);
    }
    if (p.html != null) {
      node.innerHTML = String(p.html);
    }
    if (p.title != null) {
      node.title = String(p.title);
    }
    const attrs = p.attrs as Record<string, unknown> | undefined;
    if (attrs) {
      for (const a in attrs) {
        if (Object.prototype.hasOwnProperty.call(attrs, a)) {
          node.setAttribute(a, String(attrs[a]));
        }
      }
    }
    const on = p.on as Record<string, EventListener> | undefined;
    if (on) {
      for (const ev in on) {
        if (Object.prototype.hasOwnProperty.call(on, ev)) {
          node.addEventListener(ev, on[ev]);
        }
      }
    }
    return node;
  }
  function md(text: string, hostLinks: boolean, streaming: boolean): DocumentFragment {
    if (CC.markdownFragment) {
      try {
        return CC.markdownFragment(text, { hostLinks: hostLinks, streaming: streaming });
      } catch (e) {}
    }
    const frag = document.createDocumentFragment();
    frag.appendChild(document.createTextNode(text));
    return frag;
  }

  const FENCE = /^ {0,3}(`{3,}|~{3,})(.*)$/;

  function scanBlocks(st: StreamState, text: string): number {
    let cut = st.cut;
    for (let nl = text.indexOf('\n', st.scan); nl >= 0; nl = text.indexOf('\n', st.scan)) {
      const line = text.slice(st.scan, nl);
      const fence = FENCE.exec(line);
      if (fence && !st.fence) {
        st.fence = fence[1];
      } else if (fence && fence[1].charAt(0) === st.fence.charAt(0) && fence[1].length >= st.fence.length) {
        if (!fence[2].trim()) st.fence = '';
      } else if (!st.fence && !line.trim()) {
        cut = nl + 1;
      }
      st.scan = nl + 1;
    }
    return cut;
  }

  function freshStream(body: BodyEl): StreamState {
    const st: StreamState = {
      done: el('div', { class: 'stream-done' }),
      tail: el('div', { class: 'stream-tail' }),
      prefix: '',
      cut: 0,
      scan: 0,
      fence: '',
    };
    body.replaceChildren(st.done, st.tail);
    return st;
  }

  function streamInto(rec: RowRec, body: BodyEl, text: string): void {
    let st = rec.stream;
    if (!st || st.done.parentNode !== body || !text.startsWith(st.prefix)) {
      st = rec.stream = freshStream(body);
    }
    const cut = scanBlocks(st, text);
    if (cut > st.cut) {
      st.done.appendChild(md(text.slice(st.cut, cut), rec.speaker === 'USER', true));
      st.cut = cut;
    }
    st.prefix = text.slice(0, st.scan);
    st.tail.textContent = text.slice(st.cut);
  }
  function safeSend(obj: unknown): void {
    if (CC.send) {
      try {
        CC.send(obj);
      } catch (e) {}
    }
  }
  function conversationEl(): HTMLElement | null {
    const node = (CC.els && CC.els.conversation) || document.getElementById('conversation');
    return node || null;
  }

  const rows = new Map<unknown, RowRec>();
  const toolCards = new Map<string, RowEl>();

  TX.el = el;
  TX.safeSend = safeSend;
  TX.conversationEl = conversationEl;
  TX.rows = rows;
  TX.toolCards = toolCards;

  TX.setBody = function (rec: RowRec, text: unknown, streaming?: boolean): void {
    const body = rec.bodyNode;
    if (!body) {
      return;
    }
    const kind = rec.kind;
    if (kind === 'md') {
      const raw = text == null ? '' : String(text);
      body.__rawText = raw;
      if (streaming) {
        streamInto(rec, body, raw);
        return;
      }
      rec.stream = null;
      body.replaceChildren(md(raw, rec.speaker === 'USER', false));
    } else if (kind === 'pre') {
      body.textContent = text == null ? '' : String(text);
    } else {
      body.textContent = text == null ? '' : String(text);
      body.__rawText = text == null ? '' : String(text);
    }
  };
})();
