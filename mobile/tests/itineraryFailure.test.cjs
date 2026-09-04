const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');

// Execute the screen's actual day-selection expression, including fallback helpers.
function visibleDays(itinerary) {
  const source = ts.createSourceFile('screen.tsx', fs.readFileSync('src/screens/ItineraryScreen.tsx', 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let expression;
  function visit(node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(source) === 'visibleDays') expression = node.initializer.getText(source);
    ts.forEachChild(node, visit);
  }
  visit(source);
  assert.ok(expression, 'screen must select visible days');
  const helpers = source.statements.filter(n => ts.isFunctionDeclaration(n) && n.name?.text.startsWith('preview')).map(n => n.getText(source)).join('\n');
  const code = ts.transpileModule(`${helpers}\nresult = ${expression};`, {compilerOptions: {target: ts.ScriptTarget.ES2020}}).outputText;
  const context = {itinerary, destination: 'Uncached destination', session: {getCurrentTrip: () => ({days: 2})}, cityCoordinates: () => ({latitude: 0, longitude: 0})};
  vm.runInNewContext(code, context);
  return context.result;
}

test('an unavailable itinerary never displays invented days or map coordinates', () => {
  assert.equal(visibleDays(null).length, 0);
});
test('loaded historical days remain displayable without rewriting legacy identity', () => {
  const days = [{title: 'Legacy', stops: [{placeId: null, source: 'planned_fallback', title: 'Old stop'}]}];
  assert.equal(visibleDays({days}), days);
});
