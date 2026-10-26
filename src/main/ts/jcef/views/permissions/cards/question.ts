(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const PM = (CC.permissions = CC.permissions || ({} as PermissionsNs));

  const h = CC.h;

  const OTHER = 'Other';

  function isOther(label: string): boolean {
    return label.trim().toLowerCase() === OTHER.toLowerCase();
  }

  PM.buildQuestionCard = function (card: PermissionCard): HTMLElement {
    const id = card.id;
    const questions = Array.isArray(card.questions) ? card.questions : [];
    const selections: Record<string, string[]> = {};
    const otherInputs: Record<string, HTMLInputElement> = {};

    function toggle(qText: string, label: string, multi: boolean): void {
      const arr = selections[qText];
      if (multi) {
        const i = arr.indexOf(label);
        if (i >= 0) arr.splice(i, 1);
        else arr.push(label);
      } else {
        selections[qText] = [label];
      }
      syncSelected();
      if (label === OTHER && selections[qText].indexOf(OTHER) >= 0) otherInputs[qText].focus();
    }

    function optionButton(qText: string, label: string, desc: string, preview: string, multi: boolean) {
      const props: HProps = {
        class: 'q-option',
        attrs: { type: 'button' },
        on: {
          click: function () {
            toggle(qText, label, multi);
          },
        },
      };
      if (preview) props.title = preview;
      const el = h(
        'button',
        props,
        h('span', { class: 'q-option-label', text: label }),
        desc ? h('div', { class: 'q-desc', text: desc }) : null
      ) as QuestionOptionEl;
      el.__qText = qText;
      el.__label = label;
      return el;
    }

    function otherInput(qText: string): HTMLInputElement {
      const input = h('input', {
        class: 'q-other-input',
        attrs: { type: 'text', placeholder: 'Type your own answer', hidden: 'hidden' },
      }) as HTMLInputElement;
      otherInputs[qText] = input;
      return input;
    }

    const qBlocks = questions.map(function (q) {
      const qText = q && q.question != null ? String(q.question) : '';
      const multi = !!(q && q.multiSelect);
      const options = q && Array.isArray(q.options) ? q.options : [];
      if (!(qText in selections)) selections[qText] = [];

      const optionEls = options
        .map(function (opt) {
          const label = opt && opt.label != null ? String(opt.label) : '';
          if (isOther(label)) return null;
          const desc = opt && opt.description != null ? String(opt.description) : '';
          const preview = opt && opt.preview != null ? String(opt.preview) : '';
          return optionButton(qText, label, desc, preview, multi);
        })
        .filter(Boolean) as HTMLElement[];
      optionEls.push(optionButton(qText, OTHER, 'None of these: give your own answer', '', multi));

      return h(
        'div',
        { class: 'q-block' },
        q && q.header ? h('div', { class: 'q-header', text: String(q.header) }) : null,
        h('div', { class: 'q-question', text: qText }),
        h('div', { class: 'q-options' }, optionEls),
        otherInput(qText)
      );
    });

    function answerFor(qText: string): string {
      const typed = otherInputs[qText] ? otherInputs[qText].value.trim() : '';
      return selections[qText]
        .map(function (label) {
          return label === OTHER ? typed : label;
        })
        .filter(Boolean)
        .join(', ');
    }

    const root = h(
      'div',
      { class: 'perm-card q-card' },
      h('div', { class: 'perm-head', text: card.title || card.headline || 'Question' }),
      h('div', { class: 'perm-body' }, qBlocks),
      h(
        'div',
        { class: 'perm-actions' },
        PM.button({ class: 'btn primary', text: 'Submit' }, function () {
          const answers: Record<string, string> = {};
          Object.keys(selections).forEach(function (qText) {
            answers[qText] = answerFor(qText);
          });
          PM.sendFor(card, { type: 'resolveQuestion', id: id, answers: answers });
        }),
        PM.button({ class: 'btn ghost', text: 'Cancel' }, function () {
          PM.sendFor(card, { type: 'resolvePermission', id: id, allow: false });
        })
      )
    );

    function syncSelected(): void {
      const opts = root.querySelectorAll<QuestionOptionEl>('.q-option');
      for (let i = 0; i < opts.length; i++) {
        const el = opts[i];
        const arr = (el.__qText != null && selections[el.__qText]) || [];
        if (el.__label != null && arr.indexOf(el.__label) >= 0) el.classList.add('selected');
        else el.classList.remove('selected');
      }
      Object.keys(otherInputs).forEach(function (qText) {
        if (selections[qText].indexOf(OTHER) >= 0) otherInputs[qText].removeAttribute('hidden');
        else otherInputs[qText].setAttribute('hidden', 'hidden');
      });
    }
    syncSelected();
    return root;
  };
})();
