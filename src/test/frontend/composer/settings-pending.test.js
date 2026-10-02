const { loadFrontend } = require('../helpers/load');

const payload = (sandbox) => ({
  items: [{ key: 'sandbox', group: 'Security', label: 'Sandbox commands', on: sandbox }],
});

describe('a settings row the host has not confirmed yet', () => {
  it('stays marked as pending until the next settings push', () => {
    const win = loadFrontend(['app-composer.js'], { vendor: false });
    win.CC.send = () => {};
    win.cc.settingsMenu(payload(false));
    win.document.querySelector('#views .settings-btn').click();
    const entry = Array.from(win.document.querySelectorAll('.settings-group-entry')).find(
      (e) => e.textContent === 'Security'
    );
    entry.click();
    const row = () =>
      Array.from(win.document.querySelectorAll('.settings-item')).find(
        (r) => r.textContent === 'Sandbox commands'
      );
    row().click();
    expect(row().classList.contains('pending')).toBe(true);
    expect(row().getAttribute('aria-busy')).toBe('true');
    win.cc.settingsMenu(payload(true));
    expect(row().classList.contains('pending')).toBe(false);
    expect(row().getAttribute('aria-checked')).toBe('true');
  });
});
