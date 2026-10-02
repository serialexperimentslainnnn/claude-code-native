const { loadFrontend } = require('../helpers/load');

function openFind(win) {
  win.document.dispatchEvent(new win.KeyboardEvent('keydown', { key: 'f', ctrlKey: true, bubbles: true }));
  return win.document.querySelector('.find-bar');
}

describe('controls reachable from the keyboard', () => {
  it('the find field has a name and its close is a real button', () => {
    const win = loadFrontend(['app-transcript.js']);
    const bar = openFind(win);
    expect(bar.querySelector('.find-input').getAttribute('aria-label')).toBe('Find in conversation');
    const close = bar.querySelector('.find-x');
    expect(close.tagName).toBe('BUTTON');
    close.click();
    expect(bar.hidden).toBe(true);
  });

  it('Escape during an IME composition does not close the find bar', () => {
    const win = loadFrontend(['app-transcript.js']);
    const bar = openFind(win);
    const input = bar.querySelector('.find-input');
    input.dispatchEvent(
      new win.KeyboardEvent('keydown', { key: 'Escape', isComposing: true, bubbles: true, cancelable: true })
    );
    expect(bar.hidden).toBe(false);
  });

  it('a message Copy is a real button', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([{ id: 1, order: 0, speaker: 'USER', text: 'hi', state: 'FINISHED' }]);
    expect(win.document.querySelector('.msg-head .copy').tagName).toBe('BUTTON');
  });

  it('an attachment chip is removed with a real button', () => {
    const win = loadFrontend(['app-composer.js']);
    const sent = [];
    win.CC.send = (m) => sent.push(m);
    win.cc.attachments([{ id: 'a1', kind: 'file', label: 'Foo.kt' }]);
    const x = win.document.querySelector('.att-x');
    expect(x.tagName).toBe('BUTTON');
    expect(x.getAttribute('aria-label')).toBe('Remove attachment Foo.kt');
    x.click();
    expect(sent).toContainEqual({ type: 'removeAttachment', id: 'a1' });
  });
});
