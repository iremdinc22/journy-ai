const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
const vm = require('node:vm');
const code = ts.transpileModule(fs.readFileSync('src/utils/startAreaSuggestions.ts', 'utf8'), {
  compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020},
}).outputText;
const exportsObject = {};
new Function('exports', code)(exportsObject);
const {visibleStartAreas, startAreaSelectionPayload} = exportsObject;
const a = {id:'osm_1', name:'Provider square', type:'square', latitude:46.05, longitude:14.5, source:'provider:osm', providerPlaceId:'node/1'};
const b = {...a,id:'osm_2',name:'Provider station',providerPlaceId:'node/2'};

test('empty results render zero chips and never require a start area', () => {
  assert.deepEqual(visibleStartAreas([]), []);
  assert.deepEqual(startAreaSelectionPayload(null), {});
});
test('API suggestions and selection retain exact identity and coordinates', () => {
  assert.deepEqual(visibleStartAreas([a,b]), [a,b]);
  assert.equal(visibleStartAreas([a,b],b)[0], b);
  const payload = startAreaSelectionPayload(a);
  assert.equal(payload.startingAreaSelection, a);
  assert.equal(payload.startingArea,a.name);
});
test('missing or invalid coordinates cannot create a chip', () => {
  assert.deepEqual(visibleStartAreas([{...a,latitude:0,longitude:0},{...a,latitude:NaN},{...a,source:'starter'}]), []);
});
test('editing destination or query clears the actual screen selection and old suggestions', () => {
  const text = fs.readFileSync('src/screens/TripSetupScreen.tsx','utf8');
  const source = ts.createSourceFile('screen.tsx',text,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
  const expressions = {};
  function visit(n) {
    if (ts.isVariableDeclaration(n) && ['editStartArea','changeDestination'].includes(n.name.getText(source))) expressions[n.name.getText(source)] = n.initializer.getText(source);
    ts.forEachChild(n,visit);
  }
  visit(source);
  const state={selected:a,area:a.name,suggestions:[a],city:'Old destination'};
  const context={setStartArea:value=>state.area=value,setSelectedStartArea:value=>state.selected=value,setCity:value=>state.city=value,setDestinationQuery:value=>state.query=value,setStartSuggestionPlaces:value=>state.suggestions=value};
  const script=ts.transpileModule(`const editStartArea=${expressions.editStartArea}; const changeDestination=${expressions.changeDestination}; changeDestination('New destination');`,{compilerOptions:{target:ts.ScriptTarget.ES2020}}).outputText;
  vm.runInNewContext(script,context);
  assert.equal(state.selected,null); assert.equal(state.area,''); assert.equal(state.suggestions.length,0);
  assert.doesNotMatch(text,/defaultStartSuggestions|cityStartSuggestions|fallbackStartSuggestions|starterPreviewSuggestions/);
});
