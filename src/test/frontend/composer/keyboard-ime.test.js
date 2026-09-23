const { loadFrontend } = require('../helpers/load');

function state(extra) {
  return {
    turnActive: false,
    interrupting: false,
    running: true,
    provider: { id: 'anthropic', label: 'Anthropic', options: [] },
    model: {
      label: 'Opus',
      options: [
        { value: 'opus', label: 'Opus', selected: true },
        { value: 'sonnet', label: 'Sonnet', selected: false },
        { value: 'haiku', label: 'Haiku', selected: false },
      ],
    },
    mode: { wire: 'default', label: 'Default', options: [] },
    effort: { label: 'Default', options: [] },
    thinking: { on: true, options: [] },
    queue: [],
    ...extra,
  };
}

function setup() {
  const win = loadFrontend(['app-composer.js']);
  const sent = [];
  win.CC.send = (m) => sent.push(m);
  win.cc.state(state());
  return { win, sent, doc: win.document };
}

function key(win, target, k, extra = {}) {
  const e = new win.KeyboardEvent('keydown', { key: k, bubbles: true, cancelable: true, ...extra });
  target.dispatchEvent(e);
  return e;
}

describe('pill menus from the keyboard', () => {
  it('moves focus into the list on open, walks it with the arrows and picks with Enter', () => {
    const { win, sent, doc } = setup();
    const pill = doc.querySelector('[data-pill="model"]');
    sent.length = 0;
    pill.click();
    expect(pill.getAttribute('aria-expanded')).toBe('true');
    expect(doc.activeElement.textContent).toBe('Opus');
    key(win, doc.activeElement, 'ArrowDown');
    expect(doc.activeElement.textContent).toBe('Sonnet');
    key(win, doc.activeElement, 'End');
    expect(doc.activeElement.textContent).toBe('Haiku');
    key(win, doc.activeElement, 'Enter');
    expect(sent.length).toBe(1);
    expect(doc.querySelector('.menu')).toBeNull();
    expect(doc.activeElement).toBe(pill);
  });

  it('closes on Escape from inside the list and gives focus back to the pill', () => {
    const { win, doc } = setup();
    const pill = doc.querySelector('[data-pill="model"]');
    pill.click();
    key(win, doc.activeElement, 'Escape');
    expect(doc.querySelector('.menu')).toBeNull();
    expect(doc.activeElement).toBe(pill);
    expect(pill.getAttribute('aria-expanded')).toBe('false');
  });
});

describe('the attach menu first screen', () => {
  it('reaches the actions with the arrows and keeps the query when the host pushes data', () => {
    const { win, doc } = setup();
    doc.querySelector('.attach-btn').click();
    const search = doc.querySelector('.attach-search');
    search.focus();
    search.value = 'main';
    search.dispatchEvent(new win.Event('input', { bubbles: true }));
    win.cc.attachData({ recent: [{ name: 'main.kt', path: 'src/main.kt', ext: 'kt' }] });
    expect(doc.querySelector('.attach-search')).toBe(search);
    expect(search.value).toBe('main');
    expect(doc.querySelector('.attach-recent').textContent).toContain('main.kt');
    key(win, search, 'ArrowDown');
    expect(doc.activeElement.textContent).toBe('Files…');
    key(win, doc.activeElement, 'ArrowUp');
    expect(doc.activeElement).toBe(search);
  });
});

describe('IME composition', () => {
  it('Enter that confirms a composition does not send the prompt', () => {
    const { win, sent } = setup();
    const input = win.CC.composer.els.input;
    input.value = '日本';
    key(win, input, 'Enter', { isComposing: true });
    expect(sent.filter((m) => m.type === 'send')).toEqual([]);
    key(win, input, 'Enter');
    expect(sent.filter((m) => m.type === 'send').length).toBe(1);
  });
});

describe('the composer', () => {
  it('does not take the focus when it is built', () => {
    const { win } = setup();
    expect(win.document.activeElement).not.toBe(win.CC.composer.els.input);
  });

  it('inserts text through a path that fires input', () => {
    const { win } = setup();
    const input = win.CC.composer.els.input;
    let fired = 0;
    input.addEventListener('input', () => fired++);
    win.cc.insertText('hello');
    expect(input.value).toBe('hello');
    expect(fired).toBe(1);
  });

  it('offers a real button to drop a queued prompt', () => {
    const { win, sent } = setup();
    win.cc.state(state({ queue: ['later'] }));
    const x = win.document.querySelector('.queue-x');
    expect(x.tagName).toBe('BUTTON');
    x.click();
    expect(sent).toContainEqual({ type: 'removeQueued', index: 0 });
  });

  it('keeps the palette selection when the same command list arrives again', () => {
    const { win } = setup();
    const commands = [{ name: 'a' }, { name: 'b' }];
    win.cc.meta({ commands });
    const input = win.CC.composer.els.input;
    input.value = '/';
    input.dispatchEvent(new win.Event('input', { bubbles: true }));
    key(win, input, 'ArrowDown');
    key(win, input, 'ArrowDown');
    win.cc.meta({ commands });
    expect(win.CC.composer.palette.state.active).toBe(1);
    expect(win.CC.composer.palette.state.navigated).toBe(true);
  });
});
