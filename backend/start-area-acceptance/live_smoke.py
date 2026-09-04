"""Development-only acceptance. Creates a temporary account and at most four small trips; no Place seeding."""
import argparse, json, math, time, uuid, urllib.request, urllib.error, urllib.parse
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--cities', nargs='+', default=['Edirne', 'Sarajevo', 'Ljubljana', 'Ghent'])
parser.add_argument('--output-dir', default=str(Path(__file__).parent / 'live'))
args = parser.parse_args()
out = Path(args.output_dir)
out.mkdir(exist_ok=True)
token = None

def request(path, payload=None):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request('http://localhost:8080' + path, data=None if payload is None else json.dumps(payload).encode(), headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=90) as r: return {'status': r.status, 'body': json.loads(r.read())}
    except urllib.error.HTTPError as e: return {'status': e.code, 'body': json.loads(e.read())}

identity = str(uuid.uuid4())
auth = request('/api/auth/register', {'fullName': 'Start Area Acceptance', 'email': 'start-area-' + identity + '@example.test', 'password': identity})
assert auth['status'] == 200
token = auth['body']['accessToken']
for city in args.cities:
    params = urllib.parse.urlencode({'destination':city})
    resolution = request('/api/destinations?' + urllib.parse.urlencode({'query':city}))
    first = request('/api/start-areas?' + params)
    second = request('/api/start-areas?' + params)
    result = {'input':city, 'resolution':resolution, 'suggestions':first, 'cacheRepeat':second}
    if first['status'] == 200:
        assert first['body'] == second['body']
        for p in first['body']:
            assert p['source'].startswith('provider:') and math.isfinite(p['latitude']) and math.isfinite(p['longitude'])
            assert p['name'] not in [city+' city center',city+' main station',city+' old town']
        draft = {'destination': city, 'startDate':'2026-10-10','endDate':'2026-10-11','travelerType':'SOLO','budget':'BALANCED','pace':'RELAXED','interests':['CULTURE','COFFEE','WALKING','LOCAL_FOOD']}
        if first['body']:
            selection = first['body'][0]
            draft.update(startingArea=selection['name'], startingAreaSelection=selection)
        trip = request('/api/trips',draft)
        result['trip'] = trip
        if trip['status'] == 200:
            if first['body']: assert trip['body']['startingAreaSelection'] == first['body'][0]
            result['itinerary'] = request('/api/trips/' + trip['body']['id'] + '/itinerary')
        result['syntheticFallbackCount'] = 0
    (out/(city.lower()+'.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'city':city,'status':first['status'],'count':len(first['body']) if isinstance(first['body'],list) else None,'names':[p['name'] for p in first['body']] if isinstance(first['body'],list) else [],'tripStatus':result.get('trip',{}).get('status')},ensure_ascii=False),flush=True)
for destination, query in ([('Sarajevo','Baščaršija'),('Edirne','Shinjuku')] if len(args.cities) > 1 else []):
    response = request('/api/start-areas?' + urllib.parse.urlencode({'destination':destination,'query':query}))
    (out/(destination.lower()+'-search.json')).write_text(json.dumps(response,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'destination':destination,'query':query,'status':response['status'],'count':len(response['body']) if isinstance(response['body'],list) else None},ensure_ascii=False),flush=True)
