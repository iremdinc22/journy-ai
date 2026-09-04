"""Inspect the isolated fixture. --apply tests its real endpoint, never a normal database."""
import argparse
import json
import urllib.request
import urllib.error
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--port', choices=[8080, 8082], type=int, default=8080)
parser.add_argument('--apply', action='store_true', help='Apply the preview in the disposable fixture only')
parser.add_argument('--output', type=Path)
args = parser.parse_args()
base = f'http://localhost:{args.port}'
token = None

def call(path, payload=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(base + path, headers=headers,
                                 data=None if payload is None else json.dumps(payload).encode())
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        body = error.read().decode()
        try:
            body = json.loads(body)
        except ValueError:
            pass
        return error.code, body

status, marker = call('/__dev/weather-simulator')
assert status == 200 and marker.get('fixture') == 'journy-positive-rain-v1' and marker.get('production') is False, 'Refusing a non-fixture backend'
status, auth = call('/api/auth/login', {'email': 'weather.simulator@example.test', 'password': 'WeatherDemo123!'})
assert status == 200
token = auth['accessToken']
trip_id = marker['tripId']
path = '/api/trips/' + trip_id + '/itinerary'
status, before = call(path)
assert status == 200
status, preview = call(path + '/weather-adjustment')
assert status == 200
_, repeated = call(path)
result = {'marker': marker, 'before': before, 'preview': preview, 'previewDidNotMutate': before == repeated}
assert result['previewDidNotMutate']
_, agent = call('/api/agent/message', {'tripId': trip_id, 'dayNumber': 1, 'message': 'Check the weather for today', 'language': 'en'})
result['assistantResponse'] = agent
if args.apply:
    assert preview['available'], 'Restart the fixture to restore the positive preview first'
    # Negative control: an already-serialized JSON string must still be rejected.
    status, error = call(path + '/weather-adjustment/apply', json.dumps({'previewId': preview['previewId']}))
    result['malformedDoubleEncodedNegativeControlStatus'] = status
    assert status in (400, 403)
    _, still_before = call(path)
    assert before == still_before
    status, applied = call(path + '/weather-adjustment/apply', {'previewId': preview['previewId']})
    assert status == 200
    result['correctObjectBodyApplyStatus'] = status
    _, after = call(path)
    result['after'] = after
    def identity(stop):
        return {k: v for k, v in stop.items() if k not in ('order', 'timeWindow')}
    original = before['days'][0]['stops']
    final = after['days'][0]['stops']
    assert sorted(map(identity, original), key=lambda s: s['id']) == sorted(map(identity, final), key=lambda s: s['id'])
    assert [s['placeId'] for s in final] == [s['placeId'] for s in reversed(original)]
    assert [s['timeWindow'] for s in final] == ['14:00', '16:00']
    assert before['days'][0]['walkKm'] == after['days'][0]['walkKm']
    result['onlyStopTimeAndOrderChanged'] = True
    result['walkingEstimateUnchanged'] = True
    _, post_weather = call(path + '/weather-adjustment')
    result['weatherAfterApply'] = post_weather
    assert post_weather['available'] is False
    result['timelineDiscrepancies'] = [
        {'stage': label, 'place': stop['title'], 'storedTime': stop['timeWindow'], 'timelineTime': item['startTime']}
        for label, itinerary in [('before', before), ('after', after)]
        for stop in itinerary['days'][0]['stops']
        for item in itinerary['days'][0]['timeline']
        if item['id'] == stop['id'] and item['startTime'] != stop['timeWindow']
    ]
    assert result['timelineDiscrepancies'] == [], 'Timeline must honor every persisted slot'
if args.output:
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({k: v for k, v in result.items() if k not in ['before', 'after', 'preview', 'weatherAfterApply', 'assistantResponse']}, ensure_ascii=False, indent=2))
print('weatherAdjustmentAvailable =', preview['available'])
print('Preview:', preview['changes'])
