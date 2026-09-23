(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const PM = (CC.permissions = CC.permissions || ({} as PermissionsNs));

  const h = CC.h;

  PM.buildPlanCard = function (card: PermissionCard): HTMLElement {
    const id = card.id;
    const body = h('div', { class: 'perm-body' });
    body.appendChild(PM.md(card.planText || ''));
    return h(
      'div',
      { class: 'perm-card plan-card' },
      h('div', { class: 'perm-head', text: card.title || card.headline || 'Plan' }),
      body,
      h(
        'div',
        { class: 'perm-actions' },
        PM.button({ class: 'btn primary', text: 'Approve plan' }, function () {
          PM.sendFor(card, { type: 'resolvePermission', id: id, allow: true });
        }),
        PM.button({ class: 'btn ghost', text: 'Keep planning' }, function () {
          PM.sendFor(card, { type: 'resolvePermission', id: id, allow: false });
        })
      )
    );
  };

  function renderPermDiff(text: string): HTMLElement {
    const pre = h('pre', { class: 'perm-diff' });
    const code = h('code', {});
    const lines = String(text).split('\n');
    const MAX = 400;
    const n = Math.min(lines.length, MAX);
    for (let i = 0; i < n; i++) {
      const line = lines[i];
      const c0 = line.charAt(0);
      let cls = 'dl-ctx';
      if (line.indexOf('@@') === 0) cls = 'dl-hunk';
      else if (c0 === '+') cls = 'dl-add';
      else if (c0 === '-') cls = 'dl-del';
      code.appendChild(h('span', { class: 'diff-line ' + cls, text: line + '\n' }));
    }
    if (lines.length > MAX) {
      code.appendChild(
        h('span', {
          class: 'diff-line dl-ctx',
          text: '… (' + (lines.length - MAX) + ' more lines — use View diff)\n',
        })
      );
    }
    pre.appendChild(code);
    return pre;
  }

  function buildGuardAlert(g: GuardAlertSpec): HTMLElement {
    const rule = g.rule != null ? String(g.rule) : '';
    const children = [
      h('span', { class: 'perm-guard-badge', text: 'Guard alert' }),
      h('span', {
        class: 'perm-guard-rule',
        text: String(g.label || rule) + (g.category ? ' — ' + String(g.category) : ''),
      }),
    ];
    if (g.reason) children.push(h('div', { class: 'perm-guard-reason', text: String(g.reason) }));
    if (rule) {
      children.push(
        PM.button({ class: 'perm-guard-restore', text: 'Re-enable this rule' }, function () {
          PM.send({ type: 'settingsToggle', key: 'rule:' + rule, on: true });
        })
      );
    }
    return h('div', { class: 'perm-guard' }, children);
  }

  PM.buildPermCard = function (card: PermissionCard): HTMLElement {
    const id = card.id;
    const tool = card.tool;

    const bodyChildren: HTMLElement[] = [];
    if (card.guard) bodyChildren.push(buildGuardAlert(card.guard));
    const summary = card.summary != null ? String(card.summary) : '';
    const description = card.description != null ? String(card.description) : '';
    if (summary) bodyChildren.push(h('div', { class: 'perm-summary', text: summary }));
    if (description && description !== summary) {
      const descEl = h('div', { class: 'perm-desc' });
      descEl.appendChild(PM.md(description));
      bodyChildren.push(descEl);
    }
    if (card.blockedPath)
      bodyChildren.push(
        h('div', { class: 'perm-blocked', text: 'Blocked path: ' + String(card.blockedPath) })
      );
    if (card.decisionReason)
      bodyChildren.push(h('div', { class: 'perm-reason', text: String(card.decisionReason) }));

    if (card.diff != null && String(card.diff).length) {
      bodyChildren.push(renderPermDiff(String(card.diff)));
    }

    const decisions: HTMLButtonElement[] = [];
    let root: HTMLElement | null = null;
    function decision(props: HProps, message: Record<string, unknown>): HTMLElement {
      const btn = PM.button(props, function () {
        if (btn.disabled) return;
        const hadFocus = decisions.indexOf(document.activeElement as HTMLButtonElement) >= 0;
        decisions.forEach(function (b) {
          b.disabled = true;
        });
        if (hadFocus && root) {
          root.setAttribute('tabindex', '-1');
          root.focus({ preventScroll: true });
        }
        PM.sendFor(card, message);
      }) as HTMLButtonElement;
      decisions.push(btn);
      return btn;
    }

    const actions = [
      decision({ class: 'btn primary', text: 'Accept' }, { type: 'resolvePermission', id: id, allow: true }),
      decision({ class: 'btn danger', text: 'Reject' }, { type: 'resolvePermission', id: id, allow: false }),
    ];
    if (card.reviewable) {
      actions.push(
        PM.button({ class: 'btn ghost', text: 'View diff' }, function () {
          PM.sendFor(card, { type: 'viewDiff', id: id });
        })
      );
    }
    if (card.guard) {
      actions.push(
        decision(
          { class: 'btn ghost perm-always', text: 'Always allow this command' },
          { type: 'guardAllowAlways', id: id }
        )
      );
    } else if (tool) {
      actions.push(
        decision({ class: 'btn ghost perm-always', text: 'Always allow' }, { type: 'alwaysAllow', tool: tool, id: id })
      );
    }

    root = h(
      'div',
      { class: 'perm-card' },
      h('div', { class: 'perm-head', text: card.headline || card.title || tool || 'Permission' }),
      h('div', { class: 'perm-body' }, bodyChildren),
      h('div', { class: 'perm-actions' }, actions)
    );
    return root;
  };
})();
