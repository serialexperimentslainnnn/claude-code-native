const { loadFrontend } = require('../helpers/load');

function row(id, order, speaker, text, extra = {}) {
  return { id, order, speaker, text, state: 'FINISHED', elapsed: 0, ...extra };
}

function draw(output) {
  const win = loadFrontend(['app-transcript.js']);
  win.cc.batch([
    row(1, 0, 'TOOL', 'code ▸ search_text', { meta: 'mcp__code__run', toolUseId: 't1' }),
    row(2, 1, 'TOOL_OUTPUT', JSON.stringify(output), { meta: 'toon', toolUseId: 't1' }),
  ]);
  return win.document.querySelector('.tool .tool-out .toon');
}

describe('the search_text shape: files with their matching lines', () => {
  it('opens the file at the first of several space-separated lines', () => {
    const out = draw({ files: [{ file: 'src/A.kt', lines: '12 40 77' }] });
    const link = out.querySelector('td.toon-file a.jb-link');
    expect(link.getAttribute('href')).toBe('jb://open?file=src%2FA.kt&line=12');
    expect(out.querySelector('td.toon-lines').textContent).toBe('12 40 77');
  });

  it('opens the file at a single match given as a plain integer', () => {
    const out = draw({ files: [{ file: 'src/B.kt', lines: 5 }] });
    expect(out.querySelector('td.toon-file a.jb-link').textContent).toBe('src/B.kt:5');
  });

  it('a read opens where its range starts, and its line count is never taken for a line', () => {
    const out = draw({ items: [{ path: 'src/C.kt', lines: 40, from: 12, to: 51, text: 'a\nb' }] });
    expect(out.querySelector('summary a.jb-link').getAttribute('href')).toBe('jb://open?file=src%2FC.kt&line=12');
    const top = draw({ items: [{ path: 'src/D.kt', lines: 2, from: 1, to: 2, text: 'a\nb' }] });
    expect(top.querySelector('summary a.jb-link').getAttribute('href')).toBe('jb://open?file=src%2FD.kt');
  });

  it('draws a folded batch: the clean list and the rows that carry an index', () => {
    const out = draw({ clean: ['a.kt', 'b.kt'], items: [{ index: 2, path: 'c.kt', error: 'missing' }] });
    expect(out.querySelector('.toon-clean').textContent).toContain('a.kt');
    expect(out.querySelector('.toon-item-err').textContent).toBe('missing');
  });
});
