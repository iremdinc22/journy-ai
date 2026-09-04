const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const ts=require('typescript');
function load(file, dependencies) {
 const code=ts.transpileModule(fs.readFileSync(__dirname+'/../src/'+file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText;
 const exports={};new Function('exports','require',code)(exports,name=>{assert.ok(dependencies[name],name);return dependencies[name]});return exports;
}
const session={getAccessToken:()=>null,setAuth:()=>{},setCurrentTrip:()=>{}};
const client=load('api/client.ts',{'react-native':{NativeModules:{},Platform:{OS:'ios'}},'./session':{session}});
const api=load('api/journyApi.ts',{'./client':client,'./session':{session}});
test('weather Apply sends an object serialized once and returns accepted day',async()=>{
 const saved=global.fetch;let sent;
 const accepted={dayNumber:1,stops:[{placeId:'original'}]};
 global.fetch=async(url,request)=>{sent=request;assert.ok(url.endsWith('/weather-adjustment/apply'));return new Response(JSON.stringify(accepted),{status:200});};
 try {
  assert.deepEqual(await api.tripApi.applyWeatherAdjustment('trip','abc'),accepted);
  assert.equal(sent.body,'{"previewId":"abc"}');
  assert.deepEqual(JSON.parse(sent.body),{previewId:'abc'});
  assert.equal(typeof JSON.parse(sent.body),'object');
 } finally {global.fetch=saved;}
});
test('representative auth and agent bodies retain the existing object contract',async()=>{
 const saved=global.fetch;const bodies=[];
 global.fetch=async(url,request)=>{bodies.push(JSON.parse(request.body));return new Response('{}',{status:200});};
 try {
  await api.authApi.login('test@example.test','password');
  await api.agentApi.message('weather','trip',1,'en');
  assert.deepEqual(bodies,[{email:'test@example.test',password:'password'},{tripId:'trip',dayNumber:1,message:'weather',language:'en'}]);
 } finally {global.fetch=saved;}
});
test('Plan and Day Detail both expose the exact backend timeline after a swap',()=>{
 const day={timeline:[{id:'museum',type:'STOP',startTime:'14:00'},{id:'memorial',type:'STOP',startTime:'16:00'}],stops:[]};
 for(const file of ['ItineraryScreen.tsx','DayRouteDetailScreen.tsx']) {
  const source=ts.createSourceFile(file,fs.readFileSync(__dirname+'/../src/screens/'+file,'utf8'),ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
  const helper=source.statements.find(n=>ts.isFunctionDeclaration(n)&&n.name?.text==='timelineForDay');
  const code=ts.transpileModule(helper.getText(source),{compilerOptions:{target:ts.ScriptTarget.ES2020}}).outputText;
  const result=new Function('day',code+'; return timelineForDay(day);')(day);
  assert.equal(result,day.timeline);
 }
});
