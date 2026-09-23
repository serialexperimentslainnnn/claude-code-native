const { loadFrontend } = require('../helpers/load');

const bash = (over = {}) => ({
  id: 'r1',
  tool: 'Bash',
  title: 'Bash',
  summary: 'cat notes.txt',
  headline: 'Bash',
  ...over,
});

describe('a permission card updated in place', () => {
  it('is rebuilt when the host adds a guard alert to it', () => {
    const win = loadFrontend(['app-permissions.js']);
    const region = win.CC.els.permissions;
    win.cc.permissions([bash()]);
    expect(region.querySelector('.perm-guard')).toBeNull();

    win.cc.permissions([bash({ guard: { rule: 'CREDENTIALS', label: 'Block credential files' } })]);

    expect(region.querySelectorAll('[data-card-id="r1"]').length).toBe(1);
    expect(region.querySelector('.perm-guard .perm-guard-rule').textContent).toContain('Block credential files');
  });

  it('shows a blocked path and a decision reason that arrive later', () => {
    const win = loadFrontend(['app-permissions.js']);
    const region = win.CC.els.permissions;
    win.cc.permissions([bash()]);
    win.cc.permissions([bash({ blockedPath: '/etc/shadow', decisionReason: 'outside the project' })]);

    expect(region.querySelector('.perm-blocked').textContent).toContain('/etc/shadow');
    expect(region.querySelector('.perm-reason').textContent).toContain('outside the project');
  });

  it('keeps the focus on the same control across the rebuild', () => {
    const win = loadFrontend(['app-permissions.js']);
    const region = win.CC.els.permissions;
    win.cc.permissions([bash()]);
    const reject = [...region.querySelectorAll('button')].find((b) => b.textContent === 'Reject');
    reject.focus();

    win.cc.permissions([bash({ decisionReason: 'asked by a rule' })]);

    expect(win.document.activeElement.textContent).toBe('Reject');
    expect(region.contains(win.document.activeElement)).toBe(true);
  });

  it('keeps the very same node when nothing changed', () => {
    const win = loadFrontend(['app-permissions.js']);
    const region = win.CC.els.permissions;
    win.cc.permissions([bash()]);
    const node = region.querySelector('[data-card-id="r1"]');
    win.cc.permissions([bash()]);
    expect(region.querySelector('[data-card-id="r1"]')).toBe(node);
  });
});

describe('question options say whether they are chosen', () => {
  const question = (multiSelect) => ({
    id: 'q1',
    title: 'Question',
    questions: [{ question: 'Which?', multiSelect, options: [{ label: 'A' }, { label: 'B' }] }],
  });

  it('marks the picked option pressed and the others not, inside a group named by the question', () => {
    const win = loadFrontend(['app-permissions.js']);
    win.cc.permissions([question(false)]);
    const options = [...win.CC.els.permissions.querySelectorAll('.q-option')];
    expect(options.map((o) => o.getAttribute('aria-pressed'))).toEqual(['false', 'false']);

    options[1].click();
    expect(options.map((o) => o.getAttribute('aria-pressed'))).toEqual(['false', 'true']);

    const group = options[0].parentElement;
    expect(group.getAttribute('role')).toBe('group');
    expect(win.document.getElementById(group.getAttribute('aria-labelledby')).textContent).toBe('Which?');
  });

  it('lets a multi-select question press several options', () => {
    const win = loadFrontend(['app-permissions.js']);
    win.cc.permissions([question(true)]);
    const options = [...win.CC.els.permissions.querySelectorAll('.q-option')];
    options[0].click();
    options[1].click();
    expect(options.map((o) => o.getAttribute('aria-pressed'))).toEqual(['true', 'true']);
  });
});

describe('a permission card takes one resolution', () => {
  it('disables Accept, Reject and Always allow after the first activation', () => {
    const win = loadFrontend(['app-permissions.js']);
    const sent = [];
    win.CC.send = (m) => sent.push(m);
    win.cc.permissions([bash()]);
    const byText = (t) => [...win.CC.els.permissions.querySelectorAll('button')].find((b) => b.textContent === t);

    byText('Reject').click();
    byText('Always allow').click();
    byText('Accept').click();

    expect(sent).toEqual([{ type: 'resolvePermission', id: 'r1', allow: false }]);
    expect(['Accept', 'Reject', 'Always allow'].map((t) => byText(t).disabled)).toEqual([true, true, true]);
  });

  it('is live again when the host rebuilds it with new content', () => {
    const win = loadFrontend(['app-permissions.js']);
    win.CC.send = () => {};
    win.cc.permissions([bash()]);
    const accept = () =>
      [...win.CC.els.permissions.querySelectorAll('button')].find((b) => b.textContent === 'Accept');
    accept().click();
    expect(accept().disabled).toBe(true);

    win.cc.permissions([bash({ decisionReason: 'asked again' })]);
    expect(accept().disabled).toBe(false);
  });
});
