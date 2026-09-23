const { loadFrontend } = require('../helpers/load');

describe('markdown renders to a sanitised fragment', () => {
  it('returns a DocumentFragment with the handler stripped', () => {
    const win = loadFrontend(['app-transcript.js']);
    const frag = win.CC.markdownFragment('<img src=x onerror="alert(1)"> **hi**');
    expect(frag.nodeType).toBe(11);
    const host = win.document.createElement('div');
    host.appendChild(frag);
    expect(host.querySelector('img').getAttribute('onerror')).toBeNull();
    expect(host.querySelector('strong').textContent).toBe('hi');
  });

  it('falls back to text when the sanitiser is missing', () => {
    const win = loadFrontend(['app-transcript.js']);
    delete win.DOMPurify;
    const host = win.document.createElement('div');
    host.appendChild(win.CC.markdownFragment('<img src=x>'));
    expect(host.querySelector('img')).toBeNull();
    expect(host.textContent).toBe('<img src=x>');
  });

  it('parses with the marked 18 instance API', () => {
    const win = loadFrontend(['app-transcript.js']);
    expect(typeof win.marked.Marked).toBe('function');
    const out = win.CC.markdown('- one\n- two\n\n| a | b |\n|---|---|\n| 1 | 2 |');
    expect(out).toContain('<li>one</li>');
    expect(out).toContain('<table>');
  });
});

describe('code highlighting', () => {
  it('leaves an unlabelled block as plain text instead of guessing a language', () => {
    const win = loadFrontend(['app-transcript.js']);
    const out = win.CC.markdown('```\nconst x = 1;\n```');
    expect(out).not.toMatch(/hljs-/);
    expect(out).toContain('code-head');
  });

  it('highlights a labelled block', () => {
    const win = loadFrontend(['app-transcript.js']);
    expect(win.CC.markdown('```js\nconst x = 1;\n```')).toMatch(/hljs-keyword/);
  });

  it('skips highlighting while streaming but still adds the chrome', () => {
    const win = loadFrontend(['app-transcript.js']);
    const out = win.CC.markdown('```js\nconst x = 1;\n```', { streaming: true });
    expect(out).not.toMatch(/hljs-/);
    expect(out).toContain('code-head');
  });

  it('re-highlights a decorated block whose text changed', () => {
    const win = loadFrontend(['app-transcript.js']);
    const pre = win.document.createElement('pre');
    const code = win.document.createElement('code');
    code.className = 'language-js';
    code.textContent = 'x';
    pre.appendChild(code);
    win.CC.decorateOneCodeBlock(code);
    code.textContent = 'const y = 2;';
    win.CC.decorateOneCodeBlock(code);
    expect(code.querySelector('.hljs-keyword')).not.toBeNull();
    expect(pre.querySelectorAll('.code-head').length).toBe(1);
  });

  it('returns the same markup for the same text from the cache', () => {
    const win = loadFrontend(['app-transcript.js']);
    const spy = vi.spyOn(win.hljs, 'highlight');
    const first = win.CC.highlight('val a = 1', 'kotlin');
    const second = win.CC.highlight('val a = 1', 'kotlin');
    expect(second).toBe(first);
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('answers null for an unknown language', () => {
    const win = loadFrontend(['app-transcript.js']);
    expect(win.CC.highlight('x', 'no-such-language')).toBeNull();
    expect(win.CC.highlight('x', null)).toBeNull();
  });

  it('splits highlighted markup into balanced lines', () => {
    const win = loadFrontend(['app-transcript.js']);
    const lines = win.CC.highlightLines('<span class="hljs-comment">/* a\nb */</span> x');
    expect(lines).toEqual(['<span class="hljs-comment">/* a</span>', '<span class="hljs-comment">b */</span> x']);
  });
});
