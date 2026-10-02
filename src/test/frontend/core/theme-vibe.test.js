const { loadFrontend } = require('../helpers/load');

describe('vibe mode', () => {
  it('defines the nyan artwork once, only when vibe turns on, and rows reference it', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([{ id: 1, order: 0, speaker: 'ASSISTANT', text: 'hi', state: 'FINISHED' }]);
    expect(win.document.getElementById('cc-nyan')).toBeNull();
    expect(win.document.querySelector('.avatar-nyan use').getAttribute('href')).toBe('#cc-nyan');
    win.cc.theme({ vibe: true });
    win.cc.theme({ vibe: false });
    win.cc.theme({ vibe: true });
    expect(win.document.querySelectorAll('#cc-nyan').length).toBe(1);
  });

  it('does not cycle colours when motion is reduced', () => {
    const win = loadFrontend(['app-transcript.js']);
    const spy = vi.spyOn(win, 'setInterval');
    win.cc.theme({ reducedMotion: true });
    win.cc.theme({ vibe: true });
    expect(spy).not.toHaveBeenCalled();
    win.cc.theme({ reducedMotion: false });
    expect(spy).toHaveBeenCalledTimes(1);
    win.cc.theme({ vibe: false });
  });
});
