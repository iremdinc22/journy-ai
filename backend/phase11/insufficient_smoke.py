"""Development-only overlong-trip check; preserves existing valid current trip."""
import json, urllib.request, urllib.error, sys
from pathlib import Path
token = json.loads(Path(sys.argv[1]).read_text())['accessToken']
def request(path, payload=None):
    req = urllib.request.Request('http://localhost:8080' + path,
        data=None if payload is None else json.dumps(payload).encode(),
        headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())
before = request('/api/trips/current')[1]
status, body = request('/api/trips', {'destination': 'Sarajevo', 'startingArea': '',
    'startDate': '2026-10-10', 'endDate': '2026-10-30', 'travelerType': 'SOLO',
    'budget': 'BALANCED', 'pace': 'BALANCED', 'interests': ['CULTURE','COFFEE','WALKING','LOCAL_FOOD']})
after = request('/api/trips/current')[1]
assert status == 422 and body['error'] == 'INSUFFICIENT_DESTINATION_DATA'
assert before['id'] == after['id']
result = {'status': status, 'body': body, 'currentTripBefore': before['id'], 'currentTripAfter': after['id']}
Path(__file__).with_name('live-insufficient.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result))
