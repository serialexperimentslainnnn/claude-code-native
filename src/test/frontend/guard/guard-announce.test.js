const { loadFrontend } = require('../helpers/load');

const PAYLOAD = (over = {}) => ({
  recording: true,
  window: { kept: 1, max: 500, recorded: 1, dropped: 0, missing: 0 },
  tabs: [{ id: 'blocked', label: 'Blocked', count: 1 }],
  catalog: [
    {
      id: 'secrets',
      label: 'Sensitive data',
      rules: [
        { id: 'CREDENTIALS', label: 'Block credential files' },
        { id: 'ENV_FILES', label: 'Block env files' },
      ],
    },
    { id: 'git', label: 'Git', rules: [{ id: 'DESTRUCTIVE_GIT', label: 'Block destructive Git' }] },
  ],
  entries: [
    {
      id: 'e1',
      tab: 'blocked',
      verdict: 'DENIED',
      rule: 'CREDENTIALS',
      ruleLabel: 'Block credential files',
      categoryId: 'secrets',
    },
  ],
  ...over,
});

function open() {
  const win = loadFrontend(['app-session.js', 'app-composer.js'], { vendor: false });
  win.__ccSend = () => {};
  const btn = win.document.querySelector('.dash-toggle[data-view="guard"]');
  btn.dispatchEvent(new win.MouseEvent('click', { bubbles: true }));
  return win;
}

const roles = (win) =>
  Array.from(win.document.querySelectorAll('.dashboard .guard-alarm')).map((e) => e.getAttribute('role'));

describe('guard alarms are announced once', () => {
  it('announces a new alarm, and not again when the same alarm is drawn again', () => {
    const win = open();
    win.cc.guard(PAYLOAD({ recording: false }));
    expect(roles(win)).toEqual(['alert']);

    win.cc.guard(PAYLOAD({ recording: false }));
    expect(roles(win)).toEqual([null]);
  });

  it('announces more drops as a new alarm', () => {
    const win = open();
    win.cc.guard(PAYLOAD({ window: { kept: 1, max: 500, recorded: 3, dropped: 1, missing: 0 } }));
    expect(roles(win)).toEqual(['alert']);
    win.cc.guard(PAYLOAD({ window: { kept: 1, max: 500, recorded: 3, dropped: 1, missing: 0 } }));
    expect(roles(win)).toEqual([null]);
    win.cc.guard(PAYLOAD({ window: { kept: 1, max: 500, recorded: 5, dropped: 2, missing: 0 } }));
    expect(roles(win)).toEqual(['alert']);
  });

  it('announces an alarm again once it cleared and came back', () => {
    const win = open();
    win.cc.guard(PAYLOAD({ recording: false }));
    win.cc.guard(PAYLOAD());
    expect(roles(win)).toEqual([]);
    win.cc.guard(PAYLOAD({ recording: false }));
    expect(roles(win)).toEqual(['alert']);
  });
});

describe('a guard filter menu never outlives its card', () => {
  const openMenus = (win) =>
    Array.from(win.document.querySelectorAll('.guard-filter-menu')).filter((m) => !m.hasAttribute('hidden'));

  it('closes the detached menu when a pick repaints the card, and reopens the fresh one', async () => {
    const win = open();
    win.cc.guard(PAYLOAD());
    const trigger = win.document.querySelector('.guard-filter-trigger');
    trigger.dispatchEvent(new win.MouseEvent('click', { bubbles: true }));
    const first = openMenus(win);
    expect(first.length).toBe(1);

    first[0].querySelectorAll('button')[1].dispatchEvent(new win.MouseEvent('click', { bubbles: true }));
    await new Promise((r) => setTimeout(r, 0));

    const now = openMenus(win);
    expect(now.length).toBe(1);
    expect(now[0]).not.toBe(first[0]);
    expect(first[0].hasAttribute('hidden')).toBe(true);
    const anchor = win.document.querySelector('.guard-filter-trigger[aria-expanded="true"]');
    expect(anchor).not.toBeNull();
    expect(anchor.isConnected).toBe(true);
  });

  it('closes an open menu when the host repaints the view', async () => {
    const win = open();
    win.cc.guard(PAYLOAD());
    win.document
      .querySelector('.guard-filter-trigger')
      .dispatchEvent(new win.MouseEvent('click', { bubbles: true }));
    const first = openMenus(win)[0];

    win.cc.guard(PAYLOAD());
    await new Promise((r) => setTimeout(r, 0));

    expect(first.hasAttribute('hidden')).toBe(true);
    expect(openMenus(win).every((m) => m.isConnected)).toBe(true);
  });
});
