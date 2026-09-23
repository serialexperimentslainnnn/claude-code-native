const { loadFrontend } = require('../helpers/load');

function row(id, order, speaker, text, extra = {}) {
  return { id, order, speaker, text, state: 'FINISHED', elapsed: 0, ...extra };
}

function body(win) {
  return win.document.querySelector('.msg.assistant .body');
}

function flushFrames(win) {
  win.requestAnimationFrame = (fn) => {
    fn();
    return 0;
  };
}

describe('a running row streams without full markdown', () => {
  it('renders the finished blocks and keeps the open block as plain text', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([row(1, 0, 'ASSISTANT', '**done** block\n\nstill **typing', { state: 'RUNNING' })]);
    expect(body(win).querySelector('.stream-done strong').textContent).toBe('done');
    expect(body(win).querySelector('.stream-tail').textContent).toBe('still **typing');
  });

  it('does not cut inside an open code fence', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([row(1, 0, 'ASSISTANT', '```js\nconst a = 1;\n\nconst b', { state: 'RUNNING' })]);
    expect(body(win).querySelector('.stream-done').childNodes.length).toBe(0);
    expect(body(win).querySelector('.stream-tail').textContent).toContain('const b');
  });

  it('does not highlight while streaming, and renders full markdown once it settles', () => {
    const win = loadFrontend(['app-transcript.js']);
    const text = '```js\nconst a = 1;\n```\n\nmore';
    win.cc.batch([row(1, 0, 'ASSISTANT', text, { state: 'RUNNING' })]);
    expect(body(win).querySelector('.hljs-keyword')).toBeNull();
    win.cc.batch([row(1, 0, 'ASSISTANT', text, { state: 'FINISHED' })]);
    expect(body(win).querySelector('.stream-tail')).toBeNull();
    expect(body(win).querySelector('.hljs-keyword')).not.toBeNull();
  });

  it('turns instant scrolling on while a row streams and off when it settles', () => {
    const win = loadFrontend(['app-transcript.js']);
    const conversation = win.document.getElementById('conversation');
    win.cc.batch([row(1, 0, 'ASSISTANT', 'a', { state: 'RUNNING' })]);
    expect(conversation.classList.contains('streaming')).toBe(true);
    win.cc.batch([row(1, 0, 'ASSISTANT', 'a', { state: 'FINISHED' })]);
    expect(conversation.classList.contains('streaming')).toBe(false);
  });
});

describe('cc.append', () => {
  it('appends a delta to a running row, once per frame', () => {
    const win = loadFrontend(['app-transcript.js']);
    const frames = [];
    win.requestAnimationFrame = (fn) => frames.push(fn);
    win.cc.batch([row(1, 0, 'ASSISTANT', 'Hello', { state: 'RUNNING' })]);
    frames.length = 0;
    const render = vi.spyOn(win.CC.transcript, 'setBody');
    win.cc.append({ id: 1, delta: ', wor' });
    win.cc.append({ id: 1, delta: 'ld' });
    expect(frames.length).toBe(1);
    expect(render).not.toHaveBeenCalled();
    frames.shift()();
    expect(render).toHaveBeenCalledTimes(1);
    expect(body(win).textContent).toBe('Hello, world');
  });

  it('ignores a row that is not running and an unknown id', () => {
    const win = loadFrontend(['app-transcript.js']);
    flushFrames(win);
    win.cc.batch([row(1, 0, 'ASSISTANT', 'final')]);
    win.cc.append({ id: 1, delta: ' extra' });
    win.cc.append({ id: 9, delta: 'x' });
    expect(body(win).textContent.trim()).toBe('final');
  });

  it('settles with full markdown when the batch that closes it carries the same text', () => {
    const win = loadFrontend(['app-transcript.js']);
    flushFrames(win);
    win.cc.batch([row(1, 0, 'ASSISTANT', '', { state: 'RUNNING' })]);
    win.cc.append({ id: 1, delta: '**bold**' });
    win.cc.batch([row(1, 0, 'ASSISTANT', '**bold**', { state: 'FINISHED' })]);
    expect(body(win).querySelector('strong').textContent).toBe('bold');
  });
});

describe('cc.batch', () => {
  it('keeps going past an entry that throws, and reports it', () => {
    const win = loadFrontend(['app-transcript.js']);
    const sent = [];
    win.CC.send = (m) => sent.push(m);
    const bad = row(2, 1, 'ASSISTANT', 'x');
    Object.defineProperty(bad, 'text', {
      get() {
        throw new Error('boom');
      },
    });
    win.cc.batch([row(1, 0, 'USER', 'first'), bad, row(3, 2, 'USER', 'third')]);
    const bodies = [...win.document.querySelectorAll('.msg.user .body')].map((b) => b.textContent.trim());
    expect(bodies).toEqual(['first', 'third']);
    expect(sent.some((m) => m.type === 'diag' && /boom/.test(m.report))).toBe(true);
  });

  it('accepts only an array', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch(row(1, 0, 'USER', 'lonely'));
    expect(win.document.querySelector('.msg.user')).toBeNull();
  });
});

describe('CC.emit', () => {
  it('reports a listener that throws instead of swallowing it', () => {
    const win = loadFrontend([]);
    const sent = [];
    win.CC.send = (m) => sent.push(m);
    let second = false;
    win.CC.on('probe', () => {
      throw new Error('listener broke');
    });
    win.CC.on('probe', () => {
      second = true;
    });
    win.CC.emit('probe');
    expect(second).toBe(true);
    expect(sent.some((m) => m.type === 'diag' && /listener broke/.test(m.report))).toBe(true);
  });
});

describe('cc.trimRows', () => {
  it('drops the rows of a trimmed agent card along with it', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([
      row(1, 0, 'TOOL', 'Task', { meta: 'Task', toolUseId: 'agent-1' }),
      row(2, 1, 'ASSISTANT', 'child', { parent: 'agent-1' }),
    ]);
    win.cc.trimRows({ ids: [1], total: 1 });
    expect(win.CC.transcript.rows.has(2)).toBe(false);
  });
});

describe('search and links', () => {
  it('links a path the search split across a mark', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([row(1, 0, 'ASSISTANT', 'see src/main/App.kt now')]);
    win.CC.transcript.runSearch('main', true);
    expect(body(win).querySelector('mark.cc-hit')).not.toBeNull();
    win.cc.links({ rowId: 1, links: [{ token: 'src/main/App.kt', path: 'src/main/App.kt', line: 0 }] });
    const link = body(win).querySelector('a.jb-link');
    expect(link.textContent).toBe('src/main/App.kt');
    expect(body(win).querySelector('mark.cc-hit')).not.toBeNull();
  });

  it('links every token of one reply in one pass', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([row(1, 0, 'ASSISTANT', 'a.kt and b.kt')]);
    win.cc.links({
      rowId: 1,
      links: [
        { token: 'a.kt', path: 'a.kt' },
        { token: 'b.kt', path: 'b.kt' },
      ],
    });
    expect([...body(win).querySelectorAll('a.jb-link')].map((a) => a.textContent)).toEqual(['a.kt', 'b.kt']);
  });

  it('re-searches only the rows a batch touched and keeps the hit count right', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([row(1, 0, 'USER', 'alpha'), row(2, 1, 'ASSISTANT', 'alpha beta')]);
    win.CC.transcript.runSearch('alpha', true);
    expect(win.CC.transcript.hitCount()).toBe(2);
    const first = win.document.querySelector('.msg.user .body mark');
    win.cc.batch([row(2, 1, 'ASSISTANT', 'alpha alpha')]);
    expect(win.CC.transcript.hitCount()).toBe(3);
    expect(win.document.querySelector('.msg.user .body mark')).toBe(first);
  });
});

describe('tool cards from the keyboard', () => {
  it('expose a real button that reports whether the card is open', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([row(1, 0, 'TOOL', 'Read(a.kt)', { meta: 'Read', toolUseId: 't1' })]);
    const chev = win.document.querySelector('.tool .tool-head button.chev');
    expect(chev.getAttribute('aria-expanded')).toBe('false');
    chev.click();
    expect(win.document.querySelector('.tool').classList.contains('open')).toBe(true);
    expect(chev.getAttribute('aria-expanded')).toBe('true');
  });

  it('an agent card offers to open the agent instead', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([row(1, 0, 'TOOL', 'Task', { meta: 'Task', toolUseId: 't2' })]);
    const chev = win.document.querySelector('.tool button.chev');
    expect(chev.hasAttribute('aria-expanded')).toBe(false);
    expect(chev.getAttribute('aria-label')).toBe('Open agent');
  });
});

describe('tool output', () => {
  const card = () => row(1, 0, 'TOOL', 'Bash', { meta: 'Bash', toolUseId: 't1' });

  it('skips an output whose text did not change', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([card(), row(2, 1, 'TOOL_OUTPUT', 'same', { toolUseId: 't1' })]);
    const code = win.document.querySelector('[data-out-id="to-2"] code');
    code.setAttribute('data-probe', 'kept');
    win.cc.batch([row(2, 1, 'TOOL_OUTPUT', 'same', { toolUseId: 't1' })]);
    expect(code.getAttribute('data-probe')).toBe('kept');
    expect(code.textContent).toBe('same');
  });

  it('appends only the new tail of live output', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([card(), row(2, 1, 'TOOL_OUTPUT', 'line 1\n', { toolUseId: 't1', meta: 'live' })]);
    const code = win.document.querySelector('[data-out-id="to-2"] code');
    const first = code.firstChild;
    win.cc.batch([row(2, 1, 'TOOL_OUTPUT', 'line 1\nline 2\n', { toolUseId: 't1', meta: 'live' })]);
    expect(code.firstChild).toBe(first);
    expect(code.textContent).toBe('line 1\nline 2\n');
  });

  it('highlights a diff once per side and keeps each line in its colour', () => {
    const win = loadFrontend(['app-transcript.js']);
    const spy = vi.spyOn(win.hljs, 'highlight');
    win.cc.batch([
      row(1, 0, 'TOOL', 'Edit(a.kt)', { meta: 'Edit', toolUseId: 't1', filePath: 'a.kt' }),
      row(2, 1, 'TOOL_OUTPUT', '@@ -1,3 +1,3 @@\n val a = 1\n-val b = 2\n+val b = 3\n val c = 4', {
        toolUseId: 't1',
        meta: 'diff',
      }),
    ]);
    expect(spy).toHaveBeenCalledTimes(2);
    const del = win.document.querySelector('.diff-line.dl-del');
    const add = win.document.querySelector('.diff-line.dl-add');
    expect(del.textContent).toBe('-val b = 2\n');
    expect(add.textContent).toBe('+val b = 3\n');
    expect(add.querySelector('.hljs-keyword')).not.toBeNull();
  });

  it('finds its block even when the entry id needs escaping', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([card(), row('a"b', 1, 'TOOL_OUTPUT', 'one', { toolUseId: 't1' })]);
    win.cc.batch([row('a"b', 1, 'TOOL_OUTPUT', 'two', { toolUseId: 't1' })]);
    expect(win.document.querySelectorAll('.tool-out pre').length).toBe(1);
    expect(win.document.querySelector('.tool-out pre code').textContent).toBe('two');
  });
});
