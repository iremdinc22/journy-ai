const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const ts=require('typescript');
const code=ts.transpileModule(fs.readFileSync(__dirname+'/../src/utils/weatherAdjustment.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText;
const exported={};new Function('exports',code)(exported);
const visible=exported.meaningfulWeatherAdjustment;
const valid={available:true,weatherStatus:'AVAILABLE',previewId:'preview',stopChanges:[{},{}]};
test('dry backend response hides weather actions',()=>assert.equal(visible({...valid,available:false}),false));
test('meaningful backend preview shows weather actions',()=>assert.equal(visible(valid),true));
test('unavailable missing and incomplete forecasts hide weather actions',()=>{
 for(const value of [null,undefined,{...valid,weatherStatus:'UNAVAILABLE'},{...valid,previewId:null},{...valid,stopChanges:[]}]) assert.equal(visible(value),false);
});

const vm=require('node:vm');
function screenExpression(file,name) {
 const source=ts.createSourceFile(file,fs.readFileSync(__dirname+'/../src/screens/'+file,'utf8'),ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
 let expression;
 function visit(node){if(ts.isVariableDeclaration(node)&&node.name.getText(source)===name)expression=node.initializer.getText(source);ts.forEachChild(node,visit);}
 visit(source);assert.ok(expression);
 return ts.transpileModule('result = '+expression,{compilerOptions:{target:ts.ScriptTarget.ES2020}}).outputText;
}
test('Assistant screen requires backend eligibility and the matching trip day',()=>{
 const code=screenExpression('AssistantScreen.tsx','hasWeatherRisk');
 for(const [signal,day,expected] of [[{...valid,dayNumber:1},1,true],[{...valid,available:false,dayNumber:1},1,false],[{...valid,dayNumber:2},1,false],[null,1,false]]) {
  const context={weatherSignal:signal,currentDay:{dayNumber:day},meaningfulWeatherAdjustment:visible};
  vm.runInNewContext(code,context);assert.equal(context.result,expected);
 }
});
test('Plan loads itinerary before weather and ignores the previous destination response',async()=>{
 let current={id:'Edirne'},weatherSignal=null,displayed=null,release;
 const firstForecast=new Promise(resolve=>release=resolve);
 const context={requestVersion:{current:0},useCallback:fn=>fn,
  setLoading:()=>{},setError:()=>{},setWeatherPreviewOpen:()=>{},setWeatherApplied:()=>{},setWeatherApplying:()=>{},
  setWeatherSignal:value=>weatherSignal=value,setItinerary:value=>displayed=value,
  session:{getCurrentTrip:()=>current,setCurrentTrip:value=>current=value},
  tripApi:{itinerary:async id=>({tripId:id}),weatherAdjustment:id=>id==='Edirne'?firstForecast:Promise.resolve({...valid,previewId:'Vegas'})},
  meaningfulWeatherAdjustment:visible};
 vm.runInNewContext(screenExpression('ItineraryScreen.tsx','loadItinerary'),context);
 const first=context.result();await new Promise(resolve=>setImmediate(resolve));
 assert.equal(displayed.tripId,'Edirne');assert.equal(weatherSignal,null);
 current={id:'Las Vegas'};await context.result();assert.equal(weatherSignal.previewId,'Vegas');
 release({...valid,previewId:'Edirne'});await first;
 assert.equal(displayed.tripId,'Las Vegas');assert.equal(weatherSignal.previewId,'Vegas');
});
