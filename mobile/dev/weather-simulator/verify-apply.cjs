// Execute the production Plan handler and API transport against the isolated fixture.
// This is a runtime integration test, not a claim of native simulator interaction.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const ts = require('typescript');
const root = path.resolve(__dirname, '../..');
const base = 'http://localhost:8082';
const nativeFetch = global.fetch;
let auth, trip, sentBody, acceptedDay, applied = false, weather;
const session = {
  getAccessToken: () => auth?.accessToken,
  getRefreshToken: () => auth?.refreshToken,
  setAuth: value => { auth = value; },
  setCurrentTrip: value => { trip = value; },
};
function load(relative, dependencies) {
  const code = ts.transpileModule(fs.readFileSync(path.join(root, 'src', relative), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const exports = {};
  new Function('exports', 'require', code)(exports, name => {
    assert.ok(dependencies[name], name);
    return dependencies[name];
  });
  return exports;
}
async function run() {
  const marker = await (await nativeFetch(base + '/__dev/weather-simulator')).json();
  assert.equal(marker.fixture, 'journy-positive-rain-v1');
  assert.equal(marker.production, false);
  global.fetch = (url, request) => {
    // Change only the localhost port for isolation; do not alter serialization or responses.
    assert.ok(url.startsWith('http://localhost:8080/'));
    if (url.endsWith('/weather-adjustment/apply')) sentBody = request.body;
    return nativeFetch(url.replace('http://localhost:8080', base), request);
  };
  const client = load('api/client.ts', {
    'react-native': { NativeModules: {}, Platform: { OS: 'ios' } }, './session': { session },
  });
  const { authApi, tripApi } = load('api/journyApi.ts', { './client': client, './session': { session } });
  await authApi.login('weather.simulator@example.test', 'WeatherDemo123!');
  trip = await tripApi.current();
  assert.equal(trip.id, marker.tripId);
  const before = await tripApi.itinerary(trip.id);
  weather = await tripApi.weatherAdjustment(trip.id);
  assert.equal(weather.available, true, 'Restart fixture before running this Apply test');
  assert.deepEqual(await tripApi.itinerary(trip.id), before, 'Preview must be read-only');
  const preview = weather;
  const source = ts.createSourceFile('ItineraryScreen.tsx', fs.readFileSync(path.join(root, 'src/screens/ItineraryScreen.tsx'), 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let handler;
  function visit(node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(source) === 'applyWeatherAdjustment') handler = node.initializer.getText(source);
    ts.forEachChild(node, visit);
  }
  visit(source);
  assert.ok(handler);
  const context = {
    weatherTargetDay: before.days[0], tripId: trip.id, weatherSignal: weather, tripApi,
    requestVersion: { current: 1 }, setWeatherApplying: () => {},
    updateDay: value => { acceptedDay = value; }, setWeatherApplied: value => { applied = value; },
    setWeatherSignal: update => { weather = update(weather); }, setWeatherPreviewOpen: () => {},
    t: key => key, Alert: { alert: (...args) => { throw new Error('Apply handler showed an error: ' + args.join(' ')); } },
  };
  const js = ts.transpileModule('const handler = ' + handler, { compilerOptions: { target: ts.ScriptTarget.ES2020 } }).outputText;
  await new Function(...Object.keys(context), js + '; return handler();')(...Object.values(context));
  assert.equal(applied, true);
  assert.deepEqual(JSON.parse(sentBody), { previewId: preview.previewId });
  const after = await tripApi.itinerary(trip.id);
  assert.deepEqual(after.days[0], acceptedDay);
  const identity = stop => Object.fromEntries(Object.entries(stop).filter(([key]) => !['order', 'timeWindow'].includes(key)));
  assert.deepEqual(after.days[0].stops.map(identity), before.days[0].stops.map(identity).reverse());
  assert.deepEqual(after.days[0].stops.map(stop => stop.timeWindow), ['14:00', '16:00']);
  for (const itinerary of [before, after]) {
    for (const stop of itinerary.days[0].stops) {
      assert.equal(itinerary.days[0].timeline.find(item => item.id === stop.id).startTime, stop.timeWindow);
    }
  }
  assert.equal(after.days[0].walkKm, before.days[0].walkKm);
  const finalWeather = await tripApi.weatherAdjustment(trip.id);
  assert.equal(finalWeather.available, false);
  const evidence = { date: marker.date, preview, before, after, actualWireBody: sentBody,
    productionHandlerSucceeded: applied, previewReadOnly: true, onlyTimeAndOrderChanged: true,
    timelineMatchesStoredSlots: true, walkingEstimateUnchanged: true, finalWeatherAvailable: finalWeather.available };
  fs.writeFileSync(path.join(root, '../backend/weather-acceptance/INTEGRATION_FIXTURE_RESULT.json'), JSON.stringify(evidence, null, 2) + '\n');
  console.log('PASS: real Plan handler + shared API client; single object body; successful Apply; exact identities and slots; unchanged distance; eligibility false after Apply.');
}
run().catch(error => { console.error(error); process.exitCode = 1; }).finally(() => { global.fetch = nativeFetch; });
