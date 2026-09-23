(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const CX = (CC.composer = CC.composer || ({} as ComposerNs));

  const h = CX.h;
  const send = CX.send;

  CX.openMenu = null;

  function currentOptions(def: PillDef): PillOption[] {
    if (!CX.lastState) return [];
    const f = CX.lastState[def.field] as PillField | undefined;
    if (!f || !Array.isArray(f.options)) return [];
    return f.options;
  }

  function menuSig(def: PillDef): string {
    const opts = currentOptions(def);
    let sig = opts.length + '|';
    for (let i = 0; i < opts.length; i++) {
      sig += (opts[i].selected ? '1' : '0') + (opts[i].label != null ? String(opts[i].label) : '') + '';
    }
    return sig;
  }
  CX.menuSig = menuSig;

  CX.togglePillMenu = function (def: PillDef, anchorEl: HTMLElement): void {
    if (CX.openMenu && CX.openMenu.pill === def.key) {
      closeMenu();
      return;
    }
    closeMenu();
    const opts = currentOptions(def);
    if (!opts.length) return;

    const menu = h('div', { class: 'menu', attrs: { role: 'listbox', 'aria-label': def.key } });

    function optionItem(o: PillOption): HTMLElement {
      return h(
        'div',
        {
          class: 'menu-item' + (o.selected ? ' selected' : ''),
          attrs: { role: 'option', tabindex: '-1', 'aria-selected': o.selected ? 'true' : 'false' },
          on: {
            click: function (e: Event) {
              e.preventDefault();
              e.stopPropagation();
              chooseOption(def, o);
            },
          },
        },
        h('span', { class: 'menu-item-label', text: o.label != null ? String(o.label) : '' })
      );
    }

    const main: PillOption[] = [];
    const other: PillOption[] = [];
    for (let i = 0; i < opts.length; i++) {
      (opts[i].group === 'other' ? other : main).push(opts[i]);
    }
    for (let j = 0; j < main.length; j++) menu.appendChild(optionItem(main[j]));

    if (other.length) {
      const expanded = other.some(function (o) {
        return o.selected;
      });
      const group = h('div', { class: 'menu-group' + (expanded ? ' open' : '') });
      const items = h('div', { class: 'menu-group-items' });
      const header = h(
        'div',
        {
          class: 'menu-item menu-group-header',
          attrs: { role: 'button', tabindex: '-1', 'aria-expanded': expanded ? 'true' : 'false' },
          on: {
            click: function (e: Event) {
              e.preventDefault();
              e.stopPropagation();
              const nowOpen = !group.classList.contains('open');
              group.classList.toggle('open', nowOpen);
              header.setAttribute('aria-expanded', nowOpen ? 'true' : 'false');
              if (CX.openMenu && CX.openMenu.anchor) positionMenu(menu, CX.openMenu.anchor);
            },
          },
        },
        h('span', { class: 'menu-item-label', text: 'Other models' }),
        h('span', { class: 'menu-group-caret' })
      );
      for (let k = 0; k < other.length; k++) items.appendChild(optionItem(other[k]));
      group.appendChild(header);
      group.appendChild(items);
      menu.appendChild(group);
    }

    document.body.appendChild(menu);
    menu.style.minWidth = Math.round(anchorEl.getBoundingClientRect().width) + 'px';
    positionMenu(menu, anchorEl);

    anchorEl.classList.add('pill-open');
    anchorEl.setAttribute('aria-expanded', 'true');
    CX.openMenu = { el: menu, pill: def.key, anchor: anchorEl, sig: menuSig(def) };
    menu.addEventListener('keydown', function (e: KeyboardEvent) {
      onMenuKey(menu, anchorEl, e);
    });
    const start = menu.querySelector<HTMLElement>('.menu-item.selected') || reachable(menu)[0];
    if (start) start.focus({ preventScroll: true });
  };

  function reachable(menu: HTMLElement): HTMLElement[] {
    const all = Array.prototype.slice.call(menu.querySelectorAll('.menu-item')) as HTMLElement[];
    return all.filter(function (item) {
      const group = item.closest('.menu-group-items');
      return !group || !!(group.parentElement && group.parentElement.classList.contains('open'));
    });
  }

  function onMenuKey(menu: HTMLElement, anchor: HTMLElement, e: KeyboardEvent): void {
    if (e.isComposing) return;
    const items = reachable(menu);
    const at = items.indexOf(document.activeElement as HTMLElement);
    let next = -1;
    if (e.key === 'ArrowDown') next = at < 0 ? 0 : (at + 1) % items.length;
    else if (e.key === 'ArrowUp') next = at < 0 ? items.length - 1 : (at - 1 + items.length) % items.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = items.length - 1;
    else if ((e.key === 'Enter' || e.key === ' ') && at >= 0) {
      e.preventDefault();
      items[at].click();
      if (!menu.isConnected) anchor.focus();
      else items[at].focus();
      return;
    } else if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      closeMenu();
      anchor.focus();
      return;
    } else if (e.key === 'Tab') {
      closeMenu();
      return;
    }
    if (next < 0 || !items[next]) return;
    e.preventDefault();
    items[next].focus();
  }

  const positionMenu = CC.placeMenu;
  CX.positionMenu = positionMenu;

  function chooseOption(def: PillDef, o: PillOption): void {
    closeMenu();
    send(def.msg(o));
  }

  function closeMenu(): void {
    if (!CX.openMenu) return;
    if (CX.openMenu.el && CX.openMenu.el.parentNode) CX.openMenu.el.parentNode.removeChild(CX.openMenu.el);
    if (CX.openMenu.anchor) {
      CX.openMenu.anchor.classList.remove('pill-open');
      CX.openMenu.anchor.setAttribute('aria-expanded', 'false');
    }
    CX.openMenu = null;
  }
  CX.closeMenu = closeMenu;

  document.addEventListener(
    'mousedown',
    function (e: MouseEvent) {
      if (CX.openMenu) {
        const target = e.target as Node | null;
        if (CX.openMenu.el && CX.openMenu.el.contains(target)) return;
        if (CX.openMenu.anchor && CX.openMenu.anchor.contains(target)) return;
        closeMenu();
      }
    },
    true
  );
})();
