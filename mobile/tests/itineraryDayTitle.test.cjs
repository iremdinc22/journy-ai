const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

// Run the production pure TypeScript helpers without a React Native runtime.
function loadHelper(name) {
  const filename = path.join(__dirname, '../src/utils', name + '.ts');
  const code = ts.transpileModule(readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const exports = {};
  new Function('exports', 'require', code)(exports, (dependency) => {
    if (dependency === './localizedDynamicText') return loadHelper('localizedDynamicText');
    throw new Error('Unexpected dependency: ' + dependency);
  });
  return exports;
}
const { itineraryDayTitle } = loadHelper('itineraryDayTitle');

test('whole Turkish titles preserve proper names and do not mix template fragments', () => {
  const day = { title: 'Old Town Walk & Coffee Break', titleTranslations: {
    en: 'Art: Culture & Coffee', tr: 'Art: Kültür ve Kahve',
  }, stops: [{ title: 'Art', placeId: 'osm_1', latitude: 43.86, longitude: 18.43 }] };
  const before = JSON.stringify(day);
  assert.equal(itineraryDayTitle(day, 'tr'), 'Art: Kültür ve Kahve');
  assert.equal(itineraryDayTitle(day, 'en'), 'Art: Culture & Coffee');
  assert.equal(JSON.stringify(day), before);
});

test('legacy API payloads without translations stay readable', () => {
  assert.equal(itineraryDayTitle({ title: 'Historical title' }, 'en'), 'Historical title');
  assert.equal(typeof itineraryDayTitle({ title: 'Old Town Walk & Coffee Break' }, 'tr'), 'string');
});
