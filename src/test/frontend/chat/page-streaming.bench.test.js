const fs = require('node:fs');
const path = require('node:path');
const { loadFrontend } = require('../helpers/load');

const MESSAGE_CHARS = 50 * 1024;
const DELTA_CHARS = 128;
const WARM_UP_CHARS = 8 * 1024;
const RUNS = 3;
const SEED = 42;
const RESULTS = path.resolve(__dirname, '../../../../build/bench/results.txt');
const WORDS = ['stream', 'render', 'token', 'delta', 'markdown', 'row', 'update', 'transcript', 'page'];

function seeded(seed) {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function words(random, count) {
  return Array.from({ length: count }, () => WORDS[Math.floor(random() * WORDS.length)]).join(' ');
}

function codeBlock() {
  const body = WORDS.slice(0, 6).map((word, i) => `const ${word} = ${i};`);
  return ['```js', ...body, '```'].join('\n');
}

function list(random) {
  return Array.from({ length: 4 }, () => `- **${words(random, 2)}** ${words(random, 8)}`).join('\n');
}

function block(random, index) {
  if (index % 5 === 3) return codeBlock();
  if (index % 5 === 4) return list(random);
  return words(random, 60);
}

function message(chars) {
  const random = seeded(SEED);
  const blocks = [];
  let length = 0;
  while (length < chars) {
    const next = block(random, blocks.length);
    blocks.push(next);
    length += next.length + 2;
  }
  return blocks.join('\n\n').slice(0, chars);
}

function entry(text, state) {
  return { id: 1, order: 0, speaker: 'ASSISTANT', text, state, elapsed: 0 };
}

function stream(text) {
  const win = loadFrontend(['app-transcript.js']);
  const start = performance.now();
  for (let end = DELTA_CHARS; end < text.length; end += DELTA_CHARS) {
    win.cc.batch([entry(text.slice(0, end), 'RUNNING')]);
  }
  win.cc.batch([entry(text, 'FINISHED')]);
  const elapsed = performance.now() - start;
  expect(win.document.querySelectorAll('.msg.assistant').length).toBe(1);
  return elapsed;
}

function median(samples) {
  const sorted = [...samples].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

function record(name, value, unit) {
  const line = `BENCH ${name} ${value.toFixed(2)} ${unit}`;
  console.log(line);
  fs.mkdirSync(path.dirname(RESULTS), { recursive: true });
  fs.appendFileSync(RESULTS, line + '\n');
}

describe('bench — an assistant message streamed into its transcript row', () => {
  it('streams 50 KB in 128-character deltas, one per 30 ms tick', () => {
    stream(message(WARM_UP_CHARS));
    const text = message(MESSAGE_CHARS);
    const samples = Array.from({ length: RUNS }, () => stream(text));
    record('page_streaming', median(samples), 'ms');
  }, 600000);
});
