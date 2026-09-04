const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
const vm = require('node:vm');
const text = fs.readFileSync('src/screens/ExploreScreen.tsx', 'utf8');
const source = ts.createSourceFile('screen.tsx', text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
let load;
function visit(node) {
  if (ts.isVariableDeclaration(node) && node.name.getText(source) === 'loadPlaces') load = node.initializer.arguments[0].getText(source);
  ts.forEachChild(node, visit);
}
visit(source);
function harness(options = {}) {
  const state = {places: null, loading: false, error: false, calls: []};
  const context = {
    requestVersion: {current: 0}, searchMode: true, searchQuery: 'coffee', submittedSearch: 'coffee',
    currentTrip: {destination: 'Edirne'}, activeCategory: 'For you',
    session: {restore: async () => {}, getCurrentTrip: () => ({destination: 'Edirne'})},
    exploreApi: {
      search: async (city, q) => {state.calls.push([city, q]); return [{id: 'osm_actual', name: 'Cafe'}];},
      places: async () => {state.calls.push(['browse']); return [];},
    },
    setApiPlaces: value => {state.places = value;},
    setLoading: value => {state.loading = value;},
    setError: value => {state.error = value;},
    ...options,
  };
  vm.createContext(context);
  vm.runInContext(ts.transpileModule('const run = ' + load + ';', {
    compilerOptions: {target: ts.ScriptTarget.ES2020},
  }).outputText, context);
  return {state, context, run: () => vm.runInContext('run()', context)};
}
test('search submits destination and query and retains canonical result', async () => {
  const h = harness(); await h.run();
  assert.deepEqual(h.state.calls, [['Edirne', 'coffee']]);
  assert.equal(h.state.places[0].id, 'osm_actual');
  assert.equal(h.state.loading, false);
});
test('editing without submission performs no provider search', async () => {
  const h = harness({submittedSearch: ''}); await h.run();
  assert.equal(h.state.calls.length, 0);
});
test('late result after query change cannot overwrite visible results', async () => {
  let release;
  const h = harness({exploreApi: {search: () => new Promise(resolve => {release = resolve;})}});
  const pending = h.run(); await new Promise(resolve => setImmediate(resolve));
  h.context.requestVersion.current++;
  release([{id: 'old'}]); await pending;
  assert.equal(h.state.places.length, 0);
});
test('failed search shows an error and zero results instead of preview places', async () => {
  const h = harness({exploreApi: {search: async () => {throw Error('offline');}}}); await h.run();
  assert.equal(h.state.error, true);
  assert.equal(h.state.places.length, 0);
  assert.equal(h.state.loading, false);
});
test('clearing search returns to existing Explore listing', async () => {
  const h = harness({searchMode: false, submittedSearch: '', searchQuery: ''}); await h.run();
  assert.deepEqual(h.state.calls, [['browse']]);
});

test('successful empty response renders empty state and never preview cards', async () => {
  const h = harness({exploreApi: {search: async () => []}});
  await h.run();
  assert.equal(h.state.error, false);
  assert.deepEqual(h.state.places, []);
  assert.equal(text.includes('searchMode ? [] : starterPreviewPlaces'), true);
  assert.equal(text.includes("'explore.searchEmpty'"), true);
});

test('503 keeps explicit error state', async () => {
  const h = harness({exploreApi: {search: async () => {throw Error('503');}}});
  await h.run();
  assert.equal(h.state.error, true);
  assert.equal(h.state.places.length, 0);
});

test('503 then retry with empty response clears error and retains empty result', async () => {
  let attempt = 0;
  const h = harness({exploreApi: {search: async () => {
    if (attempt++ === 0) throw Error('503');
    return [];
  }}});
  await h.run();
  assert.equal(h.state.error, true);
  await h.run();
  assert.equal(h.state.error, false);
  assert.deepEqual(h.state.places, []);
});

test('503 then retry with results clears error and renders results', async () => {
  let attempt = 0;
  const h = harness({exploreApi: {search: async () => {
    if (attempt++ === 0) throw Error('503');
    return [{id: 'osm_recovered', name: 'Recovered'}];
  }}});
  await h.run();
  await h.run();
  assert.equal(h.state.error, false);
  assert.equal(h.state.places[0].id, 'osm_recovered');
});

test('empty response followed by a new successful query renders new results', async () => {
  let attempt = 0;
  const h = harness({exploreApi: {search: async () =>
    attempt++ === 0 ? [] : [{id: 'osm_next', name: 'Next'}]
  }});
  await h.run();
  assert.deepEqual(h.state.places, []);
  await h.run();
  assert.equal(h.state.error, false);
  assert.equal(h.state.places[0].id, 'osm_next');
});

test('stale failed request cannot overwrite a newer successful response', async () => {
  let rejectOld;
  let calls = 0;
  const h = harness({exploreApi: {search: () => {
    if (calls++ === 0) return new Promise((resolve, reject) => {rejectOld = reject;});
    return Promise.resolve([]);
  }}});
  const old = h.run();
  await new Promise(resolve => setImmediate(resolve));
  const current = h.run();
  await current;
  rejectOld(Error('late 503'));
  await old;
  assert.equal(h.state.error, false);
  assert.deepEqual(h.state.places, []);
});

test('Search API uses a provider-compatible timeout without adding retry logic', () => {
  const api = fs.readFileSync('src/api/journyApi.ts', 'utf8');
  const apiSource = ts.createSourceFile('journyApi.ts', api, ts.ScriptTarget.Latest, true);
  let searchMethod = '';
  function inspect(node) {
    if (ts.isMethodDeclaration(node) && node.name.getText(apiSource) === 'search'
        && node.parent?.parent?.name?.getText(apiSource) === 'exploreApi') {
      searchMethod = node.getText(apiSource);
    }
    ts.forEachChild(node, inspect);
  }
  inspect(apiSource);
  assert.match(searchMethod, /timeoutMs:\s*60000/);
  assert.doesNotMatch(searchMethod, /retry|setTimeout|while\s*\(/);
});

test('empty-state translations match the accepted copy', () => {
  const translations = fs.readFileSync('src/i18n/translations.ts', 'utf8');
  assert.match(translations, /'explore\.searchEmpty': 'No places found'/);
  assert.match(translations, /'explore\.searchEmpty': 'Sonuç bulunamadı'/);
});
