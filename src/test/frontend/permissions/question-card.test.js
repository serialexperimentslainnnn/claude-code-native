const { loadFrontend } = require('../helpers/load');

const questionCard = (multiSelect, options) => ({
  id: 'q1',
  title: 'Question',
  questions: [{ question: 'Which one?', header: 'Pick', multiSelect, options }],
});

const optionLabels = (win) =>
  [...win.CC.els.permissions.querySelectorAll('.q-option .q-option-label')].map((el) => el.textContent);

const clickOption = (win, label) =>
  [...win.CC.els.permissions.querySelectorAll('.q-option')]
    .find((el) => el.querySelector('.q-option-label').textContent === label)
    .click();

const submit = (win) => {
  const sent = [];
  win.CC.send = (m) => sent.push(m);
  [...win.CC.els.permissions.querySelectorAll('.btn')].find((b) => b.textContent.trim() === 'Submit').click();
  return sent;
};

describe('question card — the Other answer', () => {
  it('always ends with Other, even when the model offered none', () => {
    const win = loadFrontend(['app-permissions.js']);
    win.cc.permissions([questionCard(false, [{ label: 'A' }, { label: 'B' }])]);
    expect(optionLabels(win)).toEqual(['A', 'B', 'Other']);
  });

  it('keeps a single Other when the model already offered one', () => {
    const win = loadFrontend(['app-permissions.js']);
    win.cc.permissions([questionCard(false, [{ label: 'A' }, { label: 'other' }])]);
    expect(optionLabels(win)).toEqual(['A', 'Other']);
  });

  it('shows the text field only while Other is chosen', () => {
    const win = loadFrontend(['app-permissions.js']);
    win.cc.permissions([questionCard(false, [{ label: 'A' }])]);
    const input = win.CC.els.permissions.querySelector('.q-other-input');
    expect(input.hasAttribute('hidden')).toBe(true);
    clickOption(win, 'Other');
    expect(input.hasAttribute('hidden')).toBe(false);
    clickOption(win, 'A');
    expect(input.hasAttribute('hidden')).toBe(true);
  });

  it('answers with the typed text, not the word Other', () => {
    const win = loadFrontend(['app-permissions.js']);
    win.cc.permissions([questionCard(false, [{ label: 'A' }])]);
    clickOption(win, 'Other');
    win.CC.els.permissions.querySelector('.q-other-input').value = '  something else  ';
    expect(submit(win)).toContainEqual({
      type: 'resolveQuestion',
      id: 'q1',
      answers: { 'Which one?': 'something else' },
    });
  });

  it('joins the typed text with the chosen options on a multi-select', () => {
    const win = loadFrontend(['app-permissions.js']);
    win.cc.permissions([questionCard(true, [{ label: 'A' }, { label: 'B' }])]);
    clickOption(win, 'A');
    clickOption(win, 'Other');
    win.CC.els.permissions.querySelector('.q-other-input').value = 'C';
    expect(submit(win)).toContainEqual({
      type: 'resolveQuestion',
      id: 'q1',
      answers: { 'Which one?': 'A, C' },
    });
  });
});
