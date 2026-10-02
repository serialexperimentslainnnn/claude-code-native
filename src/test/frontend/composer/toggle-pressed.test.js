const { loadFrontend } = require('../helpers/load');

function state(extra = {}) {
  return {
    turnActive: false,
    interrupting: false,
    running: true,
    guardOn: true,
    provider: { id: 'anthropic', label: 'Anthropic', options: [{ id: 'anthropic', label: 'Anthropic' }] },
    model: { label: 'Opus 5', options: [{ value: 'opus', label: 'Opus 5', selected: true }] },
    mode: { wire: 'default', label: 'Default', options: [{ wire: 'default', label: 'Default' }] },
    effort: { label: 'High', options: [{ value: 'high', label: 'High', selected: true }] },
    thinking: { on: true, label: 'Thinking on', options: [{ on: false, label: 'Off' }] },
    queue: [],
    ...extra,
  };
}

function mount(extra) {
  const win = loadFrontend(['app-composer.js']);
  win.CC.send = () => {};
  win.CC.composer.send = () => {};
  win.cc.state(state(extra));
  const byLabel = (label) => win.document.querySelector('button[aria-label="' + label + '"]');
  return { win, byLabel };
}

describe('the composer toggles say their state to assistive technology', () => {
  it('the shield is pressed while the guard is on and not while it is off', () => {
    const { win, byLabel } = mount();
    expect(byLabel('Sensitive Guard').getAttribute('aria-pressed')).toBe('true');
    win.cc.state(state({ guardOn: false }));
    expect(byLabel('Sensitive Guard').getAttribute('aria-pressed')).toBe('false');
  });

  it('auto-follow flips its pressed state with each click', () => {
    const { win, byLabel } = mount();
    const follow = byLabel('Auto-follow scrolling');
    expect(follow.getAttribute('aria-pressed')).toBe('true');
    follow.dispatchEvent(new win.MouseEvent('click', { bubbles: true }));
    expect(follow.getAttribute('aria-pressed')).toBe('false');
  });

  it('remote control starts not pressed', () => {
    const { byLabel } = mount();
    expect(byLabel('Remote Control').getAttribute('aria-pressed')).toBe('false');
  });

  it('vibe mode follows the theme the host applies', async () => {
    const { win, byLabel } = mount();
    const vibe = byLabel('Vibe Mode');
    expect(vibe.getAttribute('aria-pressed')).toBe('false');
    win.document.body.classList.add('vibe');
    win.CC.isVibe = () => true;
    await new Promise((r) => setTimeout(r, 0));
    expect(vibe.getAttribute('aria-pressed')).toBe('true');
  });
});
