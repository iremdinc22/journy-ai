"""Live local-backend weather smoke; creates an isolated development account, never applies changes."""
import json, urllib.request, urllib.error, uuid, datetime
from pathlib import Path
base='http://localhost:8080'
token=None
def call(path,payload=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request(base+path,data=None if payload is None else json.dumps(payload).encode(),headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=180) as r: return r.status,json.load(r)
    except urllib.error.HTTPError as e: return e.code,json.loads(e.read())
status,auth=call('/api/auth/register',{'fullName':'Weather acceptance','email':'weather-'+str(uuid.uuid4())+'@example.test','password':str(uuid.uuid4())})
assert status==200,status
token=auth['accessToken']
start=datetime.date.today()+datetime.timedelta(days=1)
rows=[]
for city in ['Edirne','Las Vegas','Sarajevo','Tallinn']:
    status,trip=call('/api/trips',{'destination':city,'startingArea':'','startDate':str(start),'endDate':str(start+datetime.timedelta(days=1)),'travelerType':'SOLO','budget':'BALANCED','pace':'BALANCED','interests':['CULTURE','COFFEE','LOCAL_FOOD','WALKING']})
    row={'city':city,'tripStatus':status,'trip':trip}
    if status==200:
        _,before=call('/api/trips/'+trip['id']+'/itinerary')
        row['weatherHttpStatus'],row['weather']=call('/api/trips/'+trip['id']+'/itinerary/weather-adjustment')
        _,after=call('/api/trips/'+trip['id']+'/itinerary')
        row['previewDidNotMutate']=before==after
        row['itinerary']=before
        forecast=row['weather'].get('forecast',{});hours=forecast.get('hours',[])
        row['summary']={'coordinates':[forecast.get('latitude'),forecast.get('longitude')],'timezone':forecast.get('timezone'),'dates':[forecast.get('startDate'),forecast.get('endDate')],'status':forecast.get('status'),'probabilities':sorted(set(h['precipitationProbability'] for h in hours)),'amounts':sorted(set(h['precipitationAmount'] for h in hours)),'codes':sorted(set(h['weatherCode'] for h in hours if h['weatherCode'] is not None)),'available':row['weather'].get('available'),'reason':row['weather'].get('reasons')}
    rows.append(row)
    Path('backend/weather-acceptance/LIVE_MATRIX.json').write_text(json.dumps(rows,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps({'city':city,'tripStatus':status,'summary':row.get('summary')},ensure_ascii=False),flush=True)
