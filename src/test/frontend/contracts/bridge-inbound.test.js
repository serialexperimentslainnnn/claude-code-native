const fs = require('node:fs');
const path = require('node:path');
const { loadFrontend, appJsFiles, readApp } = require('../helpers/load');
const { stripComments } = require('../helpers/source');

const KOTLIN_SRC = path.resolve(__dirname, '../../../main/kotlin');
const FRONTEND_KOTLIN_SRC = path.resolve(__dirname, '../../../../frontend/src/main/kotlin');
const SHARED_KOTLIN_SRC = path.resolve(__dirname, '../../../../shared/src/main/kotlin');
const BRIDGE_KT = path.join(KOTLIN_SRC, 'dev/lain/claudejb/model/bridge/JcefBridge.kt');

const KOTLIN_CONST = /const\s+val\s+([A-Z_][A-Z0-9_]*)\s*=\s*"([^"]+)"/g;
const KOTLIN_TYPE_PUT = /put\(\s*"type"\s*,\s*(?:"([^"]+)"|(?:[A-Za-z_][\w]*\.)?([A-Z_][A-Z0-9_]*))\s*\)/g;

const PARSED_TYPE = /^\s*"([A-Za-z_][A-Za-z0-9_]*)"\s*->/gm;

const TYPE_KEY = /\btype\s*:\s*([^,\n}]+)/g;
const QUOTED = /'([^']+)'|"([^"]+)"/g;

function parsedTypes() {
  return [...stripComments(fs.readFileSync(BRIDGE_KT, 'utf8')).matchAll(PARSED_TYPE)].map((m) => m[1]);
}

function pageSources() {
  return appJsFiles().map(readApp);
}

function pageTypeStrings(sources = pageSources()) {
  const strings = new Set();
  for (const text of sources) {
    for (const key of stripComments(text).matchAll(TYPE_KEY)) {
      for (const quoted of key[1].matchAll(QUOTED)) strings.add(quoted[1] || quoted[2]);
    }
  }
  return strings;
}

function kotlinUnder(roots) {
  return roots
    .filter((root) => fs.existsSync(root))
    .flatMap((root) =>
      fs
        .readdirSync(root, { recursive: true })
        .filter((f) => f.endsWith('.kt'))
        .map((rel) => stripComments(fs.readFileSync(path.join(root, rel), 'utf8')))
    )
    .join('\n');
}

function frontendTypeStrings(
  frontend = kotlinUnder([FRONTEND_KOTLIN_SRC]),
  shared = kotlinUnder([SHARED_KOTLIN_SRC])
) {
  const consts = new Map([...(frontend + '\n' + shared).matchAll(KOTLIN_CONST)].map((m) => [m[1], m[2]]));
  const strings = new Set();
  for (const m of frontend.matchAll(KOTLIN_TYPE_PUT)) {
    const value = m[1] || consts.get(m[2]);
    if (value) strings.add(value);
  }
  return strings;
}

function union(a, b) {
  return new Set([...a, ...b]);
}

function unsent(parsed, sent) {
  return parsed.filter((type) => !sent.has(type)).map((type) => '"' + type + '"');
}

function kotlinText() {
  return kotlinUnder([KOTLIN_SRC, FRONTEND_KOTLIN_SRC]);
}

function pageCallsTo(name, sources) {
  const call = new RegExp('\\.' + name + '\\s*\\(');
  return sources.some((text) => call.test(stripComments(text)));
}

function uncalled(registry, hostText, sources = pageSources()) {
  const host = stripComments(hostText);
  return Object.keys(registry)
    .filter((name) => typeof registry[name] === 'function')
    .filter((name) => !host.includes('window.cc.' + name) && !host.includes('"' + name + '"'))
    .filter((name) => !pageCallsTo(name, sources))
    .map((name) => 'cc.' + name);
}

describe('Kotlin↔JS bridge — page→host', () => {
  it('finds the inbound message types it is supposed to be checking', () => {
    expect(parsedTypes().length).toBeGreaterThan(20);
  });

  it('has no parsed message type that neither the page nor the frontend on its behalf sends', () => {
    expect(unsent(parsedTypes(), union(pageTypeStrings(), frontendTypeStrings()))).toEqual([]);
  });

  it('counts a type the frontend builds only through a literal or a named constant', () => {
    const frontend = [
      'fun image() = buildJsonObject { put("type", Channel.ATTACH) }',
      'fun open() = buildJsonObject { put("type", "openUrl") }',
      'val label = "notAType"',
    ].join('\n');
    const shared = 'object Channel { const val ATTACH = "attachImageData" }';

    expect([...frontendTypeStrings(frontend, shared)].sort()).toEqual(['attachImageData', 'openUrl']);
  });

  it('reports a parsed type nothing sends', () => {
    const sent = pageTypeStrings();

    expect(unsent(['ready', '__ccNoModuleSendsThis'], sent)).toEqual(['"__ccNoModuleSendsThis"']);
  });

  it('a type written in prose is a description of a message, not one being sent', () => {
    const page = [
      "// Opened by the composer: send({ type: 'only-in-a-line-comment' })",
      '/** and the block form, `{ type: "only-in-a-block-comment" }`, which the router still parses. */',
      "CC.send({ type: 'really-sent' });",
    ].join('\n');
    const sent = pageTypeStrings([page]);

    expect([...sent]).toEqual(['really-sent']);
    expect(unsent(['only-in-a-line-comment', 'only-in-a-block-comment', 'really-sent'], sent)).toEqual([
      '"only-in-a-line-comment"',
      '"only-in-a-block-comment"',
    ]);
  });
});

describe('Kotlin↔JS bridge — host→page', () => {
  it('has no cc.<name> the host and the page both ignore', () => {
    const win = loadFrontend(appJsFiles());

    expect(uncalled(win.cc, kotlinText())).toEqual([]);
  });

  it('reports a method neither side calls', () => {
    const registry = { __ccNobodyCallsThis: function () {}, trimRows: function () {} };

    expect(uncalled(registry, 'exec("window.cc.trimRows()")')).toEqual(['cc.__ccNobodyCallsThis']);
  });

  it('a method the host pushes by name, with strict JSON, counts as called', () => {
    const registry = { trimRows: function () {} };

    expect(uncalled(registry, 'exec("trimRows", json)', [])).toEqual([]);
  });

  it('a method whose only mention on either side is a comment is still uncalled', () => {
    const registry = { __ccOnlyDescribed: function () {}, __ccReallyCalled: function () {} };
    const host = '/** Was window.cc.__ccOnlyDescribed. */ exec("window.cc.__ccReallyCalled()")';
    const page = ['// the transcript used to call cc.__ccOnlyDescribed(rows) from here', 'cc.other();'];

    expect(uncalled(registry, host, page)).toEqual(['cc.__ccOnlyDescribed']);
  });
});
