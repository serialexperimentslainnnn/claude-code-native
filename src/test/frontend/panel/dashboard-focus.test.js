const { loadFrontend } = require('../helpers/load');

function setup() {
  const win = loadFrontend(['app-session.js', 'app-composer.js'], { vendor: false });
  const sent = [];
  win.__ccSend = (json) => sent.push(JSON.parse(json));
  return { win, sent, doc: win.document };
}

const panel = (win) => win.document.querySelector('.dashboard');

function openView(win, name) {
  const btn = win.document.querySelector('.dash-toggle[data-view="' + name + '"]');
  btn.dispatchEvent(new win.MouseEvent('click', { bubbles: true }));
}

const TASKS = [{ id: 't1', desc: 'npm run dev', type: 'bash', running: true, status: 'running' }];

describe('the dashboard keeps what the user is on', () => {
  it('gives focus back to the same control when its card is rebuilt', () => {
    const { win } = setup();
    win.cc.mcp({ servers: [{ name: 'alpha', status: 'connected' }] });
    win.cc.openDashboard();
    const reconnect = panel(win).querySelector('.mcp-row .btn');
    reconnect.focus();
    win.cc.mcp({ servers: [{ name: 'alpha', status: 'failed' }] });
    const now = panel(win).querySelector('.mcp-row .btn');
    expect(now).not.toBe(reconnect);
    expect(win.document.activeElement).toBe(now);
  });

  it('an MCP switch sends the state it shows now, not the one it was built with', () => {
    const { win, sent } = setup();
    win.cc.mcp({ servers: [{ name: 'alpha', status: 'connected' }] });
    win.cc.openDashboard();
    const toggle = panel(win).querySelector('.mcp-row .toggle');
    expect(toggle.tagName).toBe('BUTTON');
    toggle.setAttribute('aria-checked', 'false');
    toggle.click();
    expect(sent.pop()).toEqual({ type: 'mcpToggle', name: 'alpha', enabled: true });
  });

  it('the same workloads payload keeps the same diagram, so a drag in progress survives', () => {
    const { win } = setup();
    win.cc.session({ agentTree: [], backgroundTasks: TASKS });
    openView(win, 'workloads');
    const view = panel(win).querySelector('.dg-view');
    win.cc.session({ agentTree: [], backgroundTasks: TASKS });
    expect(panel(win).querySelector('.dg-view')).toBe(view);
  });

  it('puts the Stop control beside the node instead of inside its button', () => {
    const { win, sent } = setup();
    win.cc.session({ agentTree: [], backgroundTasks: TASKS });
    openView(win, 'workloads');
    const card = panel(win).querySelector('.dg-card.task');
    expect(card.querySelector('button, [role="button"], [tabindex]')).toBeNull();
    const stop = card.nextElementSibling;
    expect(stop.tagName).toBe('BUTTON');
    expect(stop.style.animationDelay).toBe('');
    expect(card.style.animationDelay).toBe('');
    const before = sent.length;
    stop.click();
    expect(sent.length).toBe(before + 1);
  });
});

describe('relative times', () => {
  it('are drawn once and refreshed in place by one timer', () => {
    vi.useFakeTimers();
    try {
      const { win } = setup();
      const start = Date.now();
      win.CC.dash.relFormat('test', (ms) => Math.floor(ms / 60000) + 'm');
      const node = win.CC.dash.relTime('test', start);
      win.document.body.appendChild(node);
      expect(node.textContent).toBe('0m');
      vi.advanceTimersByTime(3 * 60000);
      expect(node.textContent).toBe('3m');
    } finally {
      vi.useRealTimers();
    }
  });
});

describe('the log keeps no more lines than the host ring', () => {
  it('drops the oldest lines past the ring size', () => {
    const { win } = setup();
    const line = (seq) => ({ seq, at: 1, level: 'info', category: 'c', text: 'line ' + seq });
    win.cc.log({ reset: true, ring: { max: 3, dropped: 0 }, lines: [1, 2, 3, 4, 5].map(line) });
    expect(win.CC.dash.log.lines.map((l) => l.seq)).toEqual([3, 4, 5]);
    expect(win.CC.dash.log.list().children.length).toBe(3);
  });
});

describe('cc.setGitSubView', () => {
  it('delegates to the dashboard and ignores anything but a known view', () => {
    const { win } = setup();
    win.cc.setGitSubView('chat');
    expect(win.CC.dash.gitSubView()).toBe('chat');
    win.cc.setGitSubView(null);
    expect(win.CC.dash.gitSubView()).toBe('chat');
    win.cc.setGitSubView('overview');
    expect(win.CC.dash.gitSubView()).toBe('overview');
  });
});
