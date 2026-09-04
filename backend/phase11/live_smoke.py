"""Development-only live smoke runner. Never used by CI; creates test trips, not seed Places.
Supply a temporary auth JSON file from /api/auth/register or /api/auth/login. Tokens are not saved in reports.
"""
import argparse
import json
import time
import urllib.request
import urllib.error
import urllib.parse
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://localhost:8080')
parser.add_argument('--auth-file', required=True)
parser.add_argument('--output-dir', default=str(Path(__file__).parent / 'live'))
parser.add_argument('--cities', nargs='+', default=['Las Vegas', 'Tallinn', 'Bologna', 'Bruges', 'Antalya', 'Sarajevo', 'Copenhagen'])
args = parser.parse_args()
token = json.loads(Path(args.auth_file).read_text())['accessToken']
output = Path(args.output_dir)
output.mkdir(exist_ok=True)

def request(path, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(args.base_url + path, data=data, headers={
        'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json',
    })
    start = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            status, body = response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        status, body = error.code, error.read().decode()
    except Exception as error:
        return {'status': 0, 'seconds': round(time.monotonic()-start, 2), 'body': {'transportError': str(error)}}
    try: body = json.loads(body)
    except ValueError: pass
    return {'status': status, 'seconds': round(time.monotonic()-start, 2), 'body': body}

for city in args.cities:
    result = {'rawDestination': city, 'runs': []}
    for attempt in range(2 if city in ['Las Vegas', 'Tallinn'] else 1):
        trip = request('/api/trips', {
            'destination': city, 'startingArea': '', 'startDate': '2026-10-10', 'endDate': '2026-10-12',
            'travelerType': 'SOLO', 'budget': 'BALANCED', 'pace': 'BALANCED',
            'interests': ['CULTURE', 'COFFEE', 'LOCAL_FOOD', 'WALKING'],
        })
        run = {'attempt': attempt+1, 'trip': trip}
        if trip['status'] == 200:
            run['itinerary'] = request('/api/trips/' + trip['body']['id'] + '/itinerary')
            data = run['itinerary']['body']
            stops = [stop for day in data.get('days', []) for stop in day['stops']]
            run['summary'] = {
                'resolvedLocality': trip['body']['destination'], 'stopCount': len(stops),
                'uniquePlaceIds': len({stop['placeId'] for stop in stops}),
                'providerStops': sum(bool(stop['placeId']) and str(stop['source']).startswith('provider:') for stop in stops),
                'plannedFallback': sum(stop['source'] == 'planned_fallback' for stop in stops),
                'examples': [stop['title'] for stop in stops[:4]],
                'titlesTr': [day.get('titleTranslations', {}).get('tr') for day in data.get('days', [])],
            }
        result['runs'].append(run)
        print(json.dumps({'city': city, 'attempt': attempt+1, 'status': trip['status'], 'seconds': trip['seconds'],
                          'summary': run.get('summary', trip['body'])}, ensure_ascii=False), flush=True)
        (output / (city.lower().replace(' ', '-') + '.json')).write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
        if trip['status'] != 200: break
    # Explore is tested after planner attempts; it cannot prewarm the first request.
    result['explore'] = request('/api/explore/places?city=' + urllib.parse.quote(city))
    (output / (city.lower().replace(' ', '-') + '.json')).write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'city': city, 'exploreStatus': result['explore']['status']}, ensure_ascii=False), flush=True)
