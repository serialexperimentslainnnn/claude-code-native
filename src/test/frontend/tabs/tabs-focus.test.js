const { loadFrontend } = require('../helpers/load');

describe('the tab bar keeps the focused pill', () => {
  it('puts focus back on the same chat tab after the row is redrawn', () => {
    const win = loadFrontend(['app-tabs.js'], { vendor: false });
    win.CC.send = () => {};
    win.requestAnimationFrame = () => 0;
    const chats = (attention) => [
      { id: '1', title: 'Chat 1', selected: true },
      { id: '2', title: 'Chat 2', selected: false, attention: attention },
    ];
    win.cc.tabs({ chats: chats(false), tree: [], tasks: [] });
    const pill = () =>
      Array.from(win.document.querySelectorAll('#tabsbar button.pill')).find(
        (b) => b.querySelector('.pill-label').textContent === 'Chat 2'
      );
    const before = pill();
    before.focus();
    win.cc.tabs({ chats: chats(true), tree: [], tasks: [] });
    expect(pill()).not.toBe(before);
    expect(win.document.activeElement).toBe(pill());
  });
});
