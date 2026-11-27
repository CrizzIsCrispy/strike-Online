import asyncio, json, os, secrets, math, time, base64, lzma
from pathlib import Path
from aiohttp import web, WSMsgType

ROOT = Path(__file__).resolve().parent
GAME_HTML = (ROOT / 'public' / 'strike-online.html').read_bytes()

rooms = {}
race_rooms = {}
ACTIONS = {'scope', 'crouch', 'jump', 'unstuck', 'swap', 'grenade', 'reload', 'sprint'}

def integer(v, lo, hi):
    return type(v) is int and lo <= v <= hi

def pair(v, limit):
    return isinstance(v, list) and len(v) == 2 and all(type(n) in (int, float) and math.isfinite(n) and abs(n) <= limit for n in v)

async def index(request):
    return web.Response(body=GAME_HTML, content_type='text/html', charset='utf-8')

async def health(request):
    return web.json_response({'ok': True, 'rooms': len(rooms)})

async def send(ws, p):
    if ws is not None and not ws.closed:
        try:
            await ws.send_json(p)
        except (ConnectionError, RuntimeError):
            pass

async def websocket(request):
    from urllib.parse import urlsplit
    origin = request.headers.get('Origin')
    if origin and urlsplit(origin).netloc != request.host:
        raise web.HTTPForbidden()

    ws = web.WebSocketResponse(heartbeat=20, max_msg_size=256*1024, compress=False)
    await ws.prepare(request)
    race = request.path == '/apex/ws'
    pool = race_rooms if race else rooms
    code = None
    role = None
    seq = -1
    window = time.monotonic()
    count = 0
    try:
        async for msg in ws:
            if msg.type != WSMsgType.TEXT:
                continue
            now = time.monotonic()
            if now - window > 1:
                window, count = now, 0
            count += 1
            if count > 120:
                await ws.close(code=1008, message=b'rate limit')
                break
            try:
                m = json.loads(msg.data)
            except (ValueError, TypeError):
                continue
            if not isinstance(m, dict):
                continue
            t = m.get('type')
            if t == 'ping':
                if type(m.get('t')) in (int, float) and math.isfinite(m['t']):
                    await send(ws, {'type': 'pong', 't': m['t']})
                continue
            if role is None:
                gun = m.get('gun')
                if not integer(gun, 0, 5):
                    await send(ws, {'type': 'error', 'message': 'Invalid weapon.'})
                    continue
                if t == 'create':
                    if len(pool) >= 100:
                        await send(ws, {'type': 'error', 'message': 'Server full.'})
                        continue
                    if not integer(m.get('map'), 0, 3):
                        continue
                    code = ''.join(secrets.choice('ABCDEFGHJKLMNPQRSTUVWXYZ23456789') for _ in range(6))
                    while code in pool:
                        code = ''.join(secrets.choice('ABCDEFGHJKLMNPQRSTUVWXYZ23456789') for _ in range(6))
                    role = 'host'
                    pool[code] = {'host': ws, 'guest': None, 'map': m['map'], 'hostGun': gun}
                    await send(ws, {'type': 'room', 'room': code})
                elif t == 'join':
                    candidate = m.get('room')
                    if not isinstance(candidate, str):
                        continue
                    r = pool.get(candidate)
                    if not r or r['host'].closed or r['guest'] is not None:
                        await send(ws, {'type': 'error', 'message': 'Room not found or already full.'})
                        continue
                    code, role = candidate, 'guest'
                    r['guest'] = ws
                    start = {'type': 'start', 'room': code, 'map': r['map'], 'hostGun': r['hostGun'], 'guestGun': gun}
                    await asyncio.gather(send(r['host'], start), send(ws, start))
                continue
            r = pool.get(code)
            if not r or r['guest'] is None:
                continue
            if race:
                if role == 'guest' and t == 'drive' and integer(m.get('keys'), 0, 63):
                    await send(r['host'], {'type': 'drive', 'keys': m['keys']})
                elif role == 'host' and t == 'race' and isinstance(m.get('cars'), list) and len(m['cars']) == 2 and isinstance(m.get('traffic'), list) and len(m['traffic']) <= 100:
                    await send(r['guest'], m)
                continue
            if role == 'guest' and t == 'input':
                n, a = m.get('seq'), m.get('actions')
                if not integer(n, 0, 2**31-1) or n <= seq or not pair(m.get('move'), 1.01) or not pair(m.get('look'), 20) or type(m.get('fire')) is not bool or not isinstance(a, list) or len(a) > 20 or any(not isinstance(v, str) or v not in ACTIONS for v in a):
                    continue
                seq = n
                await send(r['host'], {k: m[k] for k in ('type','seq','move','look','fire','actions')})
            elif role == 'host' and t == 'state':
                s = m.get('state')
                if not isinstance(s, dict) or not isinstance(s.get('p'), list) or len(s['p']) != 2 or not integer(m.get('ack'), 0, 2**31-1):
                    continue
                await send(r['guest'], {'type': 'state', 'ack': m['ack'], 'state': s})
    finally:
        r = pool.get(code)
        if r and r.get(role) is ws:
            pool.pop(code, None)
            other = r['guest' if role == 'host' else 'host']
            await send(other, {'type': 'left'})
            if other is not None and not other.closed:
                await other.close()
    return ws

app = web.Application()
async def arcade_page(request):
    filename = 'apex.html' if request.path == '/apex' else 'games.html'
    return web.FileResponse(ROOT / 'public' / filename)

app.router.add_get('/games', arcade_page)
app.router.add_get('/apex', arcade_page)
app.router.add_get('/apex/ws', websocket)
app.router.add_get('/', index)
app.router.add_get('/health', health)
app.router.add_get('/ws', websocket)

if __name__ == '__main__':
    web.run_app(app, host='0.0.0.0', port=int(os.environ.get('PORT', '8080')))

