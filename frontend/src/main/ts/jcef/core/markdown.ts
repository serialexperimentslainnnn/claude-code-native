(function () {
  'use strict';

  const CC = window.CC || (window.CC = {} as CcShared);

  const WEB_LINKS =
    /^(?:(?:(?:f|ht)tps?|mailto|tel|callto|sms|cid|xmpp):|data:image\/|[^a-z]|[a-z+.-]+(?:[^a-z+.:-]|$))/i;
  const HOST_LINKS =
    /^(?:(?:(?:f|ht)tps?|mailto|tel|callto|sms|cid|xmpp|jb):|data:image\/|[^a-z]|[a-z+.-]+(?:[^a-z+.:-]|$))/i;

  let parser: MarkedParser | null = null;

  function toHtml(src: string): string {
    const md = window.marked;
    if (!md) return CC.escape(src);
    if (!parser) parser = new md.Marked({ breaks: true, gfm: true, async: false });
    return parser.parse(src) as string;
  }

  function textFragment(src: string): DocumentFragment {
    const frag = document.createDocumentFragment();
    frag.appendChild(document.createTextNode(src));
    return frag;
  }

  function sanitizedFragment(src: string, opts?: MarkdownOptions): DocumentFragment {
    let raw: string;
    try {
      raw = toHtml(src);
    } catch (e) {
      raw = CC.escape(src);
    }
    try {
      const purify = window.DOMPurify;
      if (purify) {
        return purify.sanitize(raw, {
          ADD_ATTR: ['target'],
          FORBID_ATTR: ['style'],
          ALLOWED_URI_REGEXP: opts && opts.hostLinks ? HOST_LINKS : WEB_LINKS,
          RETURN_DOM_FRAGMENT: true,
        });
      }
    } catch (e2) {}
    return textFragment(src);
  }

  CC.markdownFragment = function (text: unknown, opts?: MarkdownOptions): DocumentFragment {
    if (text === null || text === undefined) return document.createDocumentFragment();
    const frag = sanitizedFragment(String(text), opts);
    try {
      const blocks = frag.querySelectorAll('pre > code');
      for (let i = 0; i < blocks.length; i++) decorateOneCodeBlock(blocks[i], !!(opts && opts.streaming));
    } catch (e) {}
    return frag;
  };

  CC.markdown = function (text: unknown, opts?: MarkdownOptions): string {
    const holder = document.createElement('div');
    holder.appendChild(CC.markdownFragment(text, opts));
    return holder.innerHTML;
  };

  function languageOf(code: Element): string {
    const cls = (code.className || '').split(/\s+/);
    for (let c = 0; c < cls.length; c++) {
      if (cls[c].indexOf('language-') === 0) return cls[c].slice('language-'.length);
    }
    return '';
  }

  function codeHead(lang: string): HTMLElement {
    const head = document.createElement('div');
    head.className = 'code-head';
    const label = document.createElement('span');
    label.className = 'code-lang';
    label.textContent = lang || 'text';
    head.appendChild(label);
    const copy = document.createElement('span');
    copy.className = 'copy';
    copy.setAttribute('role', 'button');
    copy.setAttribute('tabindex', '0');
    copy.textContent = 'Copy';
    head.appendChild(copy);
    return head;
  }

  function decorateOneCodeBlock(code: Element, plain?: boolean): void {
    const pre = code && (code.parentNode as Element | null);
    if (!pre) return;
    const lang = languageOf(code);
    if (pre.getAttribute('data-cc-decorated') !== '1') {
      pre.setAttribute('data-cc-decorated', '1');
      pre.insertBefore(codeHead(lang), code);
    }
    if (plain) return;
    const html = CC.highlight(code.textContent || '', lang);
    if (html !== null) code.innerHTML = html;
  }
  CC.decorateOneCodeBlock = decorateOneCodeBlock;

  const CACHE_CHARS = 4000000;
  const highlighted = new Map<string, string>();
  let cachedChars = 0;

  function remember(key: string, html: string): void {
    const size = key.length + html.length;
    if (size > CACHE_CHARS / 8) return;
    while (cachedChars + size > CACHE_CHARS && highlighted.size) {
      const oldest = highlighted.keys().next().value as string;
      cachedChars -= oldest.length + (highlighted.get(oldest) || '').length;
      highlighted.delete(oldest);
    }
    highlighted.set(key, html);
    cachedChars += size;
  }

  CC.highlight = function (text: string, lang: string | null | undefined): string | null {
    const hl = window.hljs;
    if (!lang || !hl || typeof hl.getLanguage !== 'function' || !hl.getLanguage(lang)) return null;
    const key = lang + '\u0000' + text;
    const hit = highlighted.get(key);
    if (hit !== undefined) return hit;
    try {
      const html = hl.highlight(text, { language: lang, ignoreIllegals: true }).value;
      remember(key, html);
      return html;
    } catch (e) {
      return null;
    }
  };

  const HTML_TOKEN = /<span[^>]*>|<\/span>|[^<]+/g;

  CC.highlightLines = function (html: string): string[] {
    const lines: string[] = [];
    const open: string[] = [];
    let line = '';
    const tokens = html.match(HTML_TOKEN) || [];
    for (let t = 0; t < tokens.length; t++) {
      const token = tokens[t];
      if (token.charAt(0) === '<') {
        if (token.charAt(1) === '/') open.pop();
        else open.push(token);
        line += token;
        continue;
      }
      const parts = token.split('\n');
      for (let p = 0; p < parts.length; p++) {
        if (p > 0) {
          lines.push(line + '</span>'.repeat(open.length));
          line = open.join('');
        }
        line += parts[p];
      }
    }
    lines.push(line + '</span>'.repeat(open.length));
    return lines;
  };

  const EXT_LANG: Record<string, string> = {
    kt: 'kotlin',
    kts: 'kotlin',
    java: 'java',
    js: 'javascript',
    mjs: 'javascript',
    cjs: 'javascript',
    jsx: 'javascript',
    ts: 'typescript',
    tsx: 'typescript',
    json: 'json',
    xml: 'xml',
    html: 'xml',
    htm: 'xml',
    svg: 'xml',
    xsd: 'xml',
    xsl: 'xml',
    plist: 'xml',
    yml: 'yaml',
    yaml: 'yaml',
    sh: 'bash',
    bash: 'bash',
    zsh: 'bash',
    py: 'python',
    rb: 'ruby',
    go: 'go',
    rs: 'rust',
    c: 'c',
    h: 'c',
    cpp: 'cpp',
    cc: 'cpp',
    cxx: 'cpp',
    hpp: 'cpp',
    hh: 'cpp',
    cs: 'csharp',
    php: 'php',
    pl: 'perl',
    pm: 'perl',
    lua: 'lua',
    sql: 'sql',
    css: 'css',
    scss: 'scss',
    less: 'less',
    md: 'markdown',
    markdown: 'markdown',
    ini: 'ini',
    cfg: 'ini',
    conf: 'ini',
    properties: 'ini',
    swift: 'swift',
    r: 'r',
    graphql: 'graphql',
    gql: 'graphql',
    vb: 'vbnet',
    wasm: 'wasm',
    wat: 'wasm',
    m: 'objectivec',
    mm: 'objectivec',
    txt: 'plaintext',
  };
  CC.languageForPath = function (path: unknown): string | null {
    const p = String(path || '');
    const base = p.split(/[\\/]/).pop() || '';
    if (/^makefile$/i.test(base)) {
      return 'makefile';
    }
    const dot = base.lastIndexOf('.');
    if (dot < 0 || dot === base.length - 1) {
      return null;
    }
    const ext = base.slice(dot + 1).toLowerCase();
    return EXT_LANG[ext] || null;
  };
})();
