const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
function load(relative, dependencies = {}) {
  const code = ts.transpileModule(readFileSync(path.join(__dirname, '../src', relative), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const exports = {};
  new Function('exports', 'require', code)(exports, name => {
    if (dependencies[name]) return dependencies[name];
    throw new Error('Unexpected dependency: ' + name);
  });
  return exports;
}
const client = load('api/client.ts', {
  'react-native': { NativeModules: {}, Platform: { OS: 'ios' } },
  './session': { session: { getAccessToken: () => null } },
});
const { loadingPlanErrorMessage } = load('utils/loadingPlanErrorMessage.ts', { '../api/client': client });
const t = key => key;

test('422 is not successful trip creation and preserves the machine-readable error for localized display', async () => {
  const savedFetch = global.fetch;
  global.fetch = async () => new Response(JSON.stringify({
    error: 'INSUFFICIENT_DESTINATION_DATA', message: 'Not enough reliable places', status: 422,
  }), { status: 422 });
  try {
    await assert.rejects(client.apiRequest('/api/trips', { method: 'POST', body: {}, auth: false }), error => {
      assert.equal(error.status, 422);
      assert.equal(error.code, 'INSUFFICIENT_DESTINATION_DATA');
      assert.equal(loadingPlanErrorMessage(error, t), 'loading.insufficientData');
      return true;
    });
  } finally { global.fetch = savedFetch; }
});

test('existing authentication, validation and network errors retain their messages', () => {
  assert.equal(loadingPlanErrorMessage(new client.ApiError(401, ''), t), 'loading.sessionExpired');
  assert.equal(loadingPlanErrorMessage(new client.ApiError(403, ''), t), 'loading.freshSignIn');
  assert.equal(loadingPlanErrorMessage(new client.ApiError(400, 'Invalid dates'), t), 'Invalid dates');
  assert.equal(loadingPlanErrorMessage(new client.NetworkError(), t), 'loading.networkError');
});
