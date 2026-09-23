const { loadFrontend } = require('../helpers/load');

describe('links clicked in the page', () => {
  let win;
  let sent = [];

  beforeAll(() => {
    win = loadFrontend([]);
    win.CC.send = (m) => sent.push(m);
  });

  beforeEach(() => {
    sent = [];
  });

  const link = (href) => {
    const a = win.document.createElement('a');
    a.setAttribute('href', href);
    a.textContent = 'x';
    win.document.body.appendChild(a);
    return a;
  };

  const click = (a, type = 'click', button = 0) => {
    const ev = new win.MouseEvent(type, { bubbles: true, cancelable: true, button });
    a.dispatchEvent(ev);
    return ev;
  };

  it.each([
    'https://example.com/a',
    'http://example.com/a',
    'jb://commit?hash=abc',
    'src/main/App.kt',
    'src/main/App.kt#L3',
  ])('forwards %s to the host', (href) => {
    click(link(href));
    expect(sent).toEqual([{ type: 'open', url: href }]);
  });

  it.each([
    'data:image/png;base64,AAAA',
    'data:text/html,<script>1</script>',
    'javascript:alert(1)',
    'file:///etc/passwd',
    'jb:open',
    'mailto:a@b.c',
    '//evil.example/x',
    '\\\\server\\share',
  ])('drops %s and keeps the page where it is', (href) => {
    const ev = click(link(href));
    expect(sent).toEqual([]);
    expect(ev.defaultPrevented).toBe(true);
  });

  it('intercepts a middle click the same way', () => {
    const ev = click(link('data:image/png;base64,AAAA'), 'auxclick', 1);
    expect(ev.defaultPrevented).toBe(true);
    click(link('https://example.com/b'), 'auxclick', 1);
    expect(sent).toEqual([{ type: 'open', url: 'https://example.com/b' }]);
  });

  it('leaves a right click to the context menu', () => {
    const ev = click(link('https://example.com/c'), 'auxclick', 2);
    expect(ev.defaultPrevented).toBe(false);
    expect(sent).toEqual([]);
  });

  it('lets an in-page anchor scroll the page without bothering the host', () => {
    const ev = click(link('#section'));
    expect(ev.defaultPrevented).toBe(false);
    expect(sent).toEqual([]);
  });
});
