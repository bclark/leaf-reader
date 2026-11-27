"""Owned loopback-only cinematic UI fixture.

Run: LEAF_DETAILS_SCENARIO=normal (or retry) python3 iosApp/ci/jellyfin-ui-fixture.py OUTPUT_DIRECTORY.
Use only a fresh named Leaf Onboarding QA simulator; see FirstLaunchOnboardingUITests.
Never logs bodies or authorization headers; no network outside loopback.
"""
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlsplit, parse_qs
import datetime, hashlib, json, os, sys, threading, zipfile, struct, zlib, time

p = Path(__file__).resolve().parent
out = Path(sys.argv[1])
out.mkdir(exist_ok=False)
payload = (p / 'SyntheticManga.cbz').read_bytes()
with zipfile.ZipFile(p / 'SyntheticManga.cbz') as archive:
    name = next(n for n in archive.namelist() if n.lower().endswith(('.png', '.jpg', '.jpeg')))
    cover = archive.read(name)
    cover_type = 'image/png' if name.lower().endswith('.png') else 'image/jpeg'
sign_in_failures = int(os.environ.get('LEAF_SIGNIN_FAILURES', '0'))
assert sign_in_failures in {0, 1}
scenario = os.environ.get('LEAF_DETAILS_SCENARIO', 'normal')
assert scenario in {'normal', 'retry', 'peel', 'onboarding', 'appearance', 'quality'}
writer = 'Alexandra Longname Example de la Biblioteca'
title = 'QA A Very Long Comic Title for the Cinematic Detail Layout and Creator Navigation'
main = {'Id': 'fixture-book', 'Name': title, 'IsFolder': False, 'Type': 'Book', 'Container': 'cbz',
        'ParentId': 'fixture-library', 'ImageTags': {'Primary': 'qa-cover'},
        'SeriesName': 'QA Cinematic Series', 'IndexNumber': 12, 'ProductionYear': 2026,
        'Overview': 'QA synopsis with enough text to exercise scrolling and return position. ' * 12,
        'People': [{'Id': 'fixture-writer', 'Name': writer, 'Type': 'Writer'},
                   {'Id': 'fixture-artist', 'Name': 'Jordan Example', 'Type': 'Artist'},
                   {'Id': 'fixture-neutral', 'Name': 'Morgan Example'}],
        'Genres': ['Adventure', 'Speculative Fiction'],
        'UserData': {'Played': False, 'PlaybackPositionTicks': 10000, 'PlayedPercentage': 25,
                     'LastPlayedDate': '2026-11-14T12:00:00Z'}}
if scenario == 'onboarding':
    main.update(Name='QA Server Continue', Overview='Server-only continuation details.')
match = {'Id': 'fixture-match', 'Name': 'QA Creator Match', 'IsFolder': False, 'Type': 'Book',
         'Container': 'cbz', 'ParentId': 'fixture-library', 'UserData': {'Played': False}}
appearance_books = [main, match, {**match, 'Id': 'fixture-unknown', 'Name': 'QA Unknown Reading State', 'UserData': None},
                    {**match, 'Id': 'fixture-finished', 'Name': 'QA Finished Reading State', 'UserData': {'Played': True}}]
peel_books = [{**main, 'UserData': main['UserData'] if i == 0 else {'Played': False}, 'Id': 'peel-' + str(i), 'Name': 'QA Peel Comic ' + str(i + 1),
               'SeriesName': 'QA Peel Series', 'IndexNumber': i + 1,
               'ImageTags': {'Primary': 'peel-color-' + str(i)}} for i in range(3)]
cover_calls = {}
def color_png(rgb):
    width, height = 280, 400
    def chunk(kind, value):
        return struct.pack('>I', len(value)) + kind + value + struct.pack('>I', zlib.crc32(kind + value))
    scan = (b'\x00' + bytes(rgb) * width) * height
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(scan)) + chunk(b'IEND', b'')
peel_colors = [(190, 40, 40), (35, 145, 75), (35, 75, 190)]
# A high-resolution synthetic test chart: no private/copyrighted comic is used.
if scenario == 'quality':
    width, height = 1680, 2400
    def chunk(kind, value):
        return struct.pack('>I', len(value)) + kind + value + struct.pack('>I', zlib.crc32(kind + value))
    rows = []
    for y in range(height):
        line = bytearray()
        for x in range(width):
            ink = (x % 24 < 3 or y % 24 < 3)
            line.extend((20, 35, 50) if ink else (245, 238, 212))
        rows.append(b'\x00' + line)
    cover = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(b''.join(rows))) + chunk(b'IEND', b'')
    cover_type = 'image/png'

requests = []
detail_calls = 0
creator_calls = 0

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def record(self):
        parts = urlsplit(self.path)
        query = parse_qs(parts.query)
        requests.append({'method': self.command, 'path': parts.path,
                         'query': {k: v for k, v in query.items() if k in {'PersonIds', 'Person', 'ParentId', 'StartIndex', 'Limit', 'IncludeItemTypes', 'maxWidth'}},
                         'utc': datetime.datetime.now(datetime.timezone.utc).isoformat()})
        return parts.path, query
    def reply(self, status, value, content_type='application/json'):
        body = value if isinstance(value, bytes) else json.dumps(value).encode()
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        try: self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError): pass
    def do_POST(self):
        global sign_in_failures
        path, _ = self.record()
        body = self.rfile.read(min(int(self.headers.get('Content-Length', '0')), 4096))
        if path != '/Users/AuthenticateByName': self.reply(404, {}); return
        try: credentials = json.loads(body)
        except ValueError: self.reply(400, {}); return
        if credentials.get('Username') != 'fixture' or credentials.get('Pw') != 'fixture': self.reply(401, {}); return
        if sign_in_failures:
            sign_in_failures -= 1
            self.reply(503, {}); return
        self.reply(200, {'AccessToken': 'leaf-onboarding-fixture-token', 'User': {'Id': 'fixture-user', 'Name': 'fixture'}, 'ServerId': 'fixture-server'})
    def do_GET(self):
        global detail_calls, creator_calls
        path, query = self.record()
        if path == '/control/stop':
            self.reply(200, {'stopped': True})
            threading.Thread(target=self.server.shutdown, daemon=True).start(); return
        if 'leaf-onboarding-fixture-token' not in self.headers.get('Authorization', ''): self.reply(401, {}); return
        if path == '/Users/fixture-user/Views':
            self.reply(200, {'Items': [{'Id': 'fixture-library', 'Name': 'Fixture Library', 'Type': 'CollectionFolder', 'IsFolder': True, 'CollectionType': 'books'}], 'TotalRecordCount': 1}); return
        if scenario == 'peel' and '/Images/Primary' in path and path.startswith('/Items/peel-'):
            item_id = path.split('/')[2]
            index = int(item_id.split('-')[1])
            cover_calls[item_id] = cover_calls.get(item_id, 0) + 1
            if index != 0 and cover_calls[item_id] > 1: time.sleep(3)
            self.reply(200, color_png(peel_colors[index]), 'image/png'); return
        if scenario == 'peel' and path.startswith('/Items/peel-'):
            self.reply(200, peel_books[int(path.split('/')[2].split('-')[1])]); return
        if scenario == 'appearance' and path == '/Items/fixture-book/Download':
            self.reply(200, payload, 'application/zip'); return
        if path == '/Items/fixture-book':
            detail_calls += 1
            if scenario == 'retry' and detail_calls == 2: self.reply(503, {}); return
            self.reply(200, main); return
        if path == '/Items/fixture-match': self.reply(200, match); return
        if path == '/Items/fixture-book/Images/Primary': self.reply(200, cover, cover_type); return
        if path.endswith('/Ancestors'):
            self.reply(200, [{'Id': 'fixture-library', 'Name': 'Fixture Library', 'IsFolder': True, 'Type': 'CollectionFolder'}]); return
        if path == '/Items' or path.startswith('/Users/fixture-user/Items'):
            if 'PersonIds' in query or 'Person' in query:
                assert query.get('ParentId') == ['fixture-library'], 'Keep creator query scoped to the fixture library'
                creator_calls += 1
                if scenario == 'retry' and creator_calls == 1: self.reply(503, {}); return
                entries = [main, match]
            else: entries = [] if query.get('IncludeItemTypes') == ['BoxSet'] else (peel_books if scenario == 'peel' else appearance_books if scenario == 'appearance' else [main])
            self.reply(200, {'Items': entries, 'TotalRecordCount': len(entries)}); return
        self.reply(404, {})

server = HTTPServer(('127.0.0.1', 56823), Handler)
(out / 'ready.json').write_text(json.dumps({'url': 'http://127.0.0.1:56823', 'pid': os.getpid(), 'scenario': scenario,
                                         'fixtureSHA256': hashlib.sha256(payload).hexdigest(), 'scriptSHA256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}, indent=2) + '\n')
try: server.serve_forever(poll_interval=0.1)
except KeyboardInterrupt: pass
finally:
    server.server_close()
    (out / 'requests.json').write_text(json.dumps(requests, indent=2) + '\n')
