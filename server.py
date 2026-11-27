import asyncio, json, os, secrets, math, time, base64, lzma
from pathlib import Path
from aiohttp import web, WSMsgType

ROOT = Path(__file__).resolve().parent
GAME_HTML = lzma.decompress(base64.b64decode(''.join((ROOT / 'public' / ('game_%02d.txt' % i)).read_text() for i in range(1, 8))))
# Apply stair hotfix to the original release at startup; fail if source differs.
_stair_replacements = [["let houseSolids=[],houseStairs=[]","let outdoorStairs=[],houseSolids=[],houseStairs=[]"],["function buildMap(){houseSolids=[];houseStairs=[];","function buildMap(){outdoorStairs=[];houseSolids=[];houseStairs=[];"],["walls.push([tx,tz,3,3,2.8]);for(let n=0;n<14;n++)walls.push([tx,tz+sign*(5.4-n*.3),1.65,.31,(n+1)*.2]);","walls.push([tx,tz,3,3,2.8]);outdoorStairs.push({x:tx,z:tz+sign*5.55,dir:-sign,run:4.2,w:2.1,h:2.8});for(let n=0;n<14;n++)walls.push([tx,tz+sign*(5.4-n*.3),2.1,.31,(n+1)*.2]);"],["walls.push([0,0,3,3,2.4]);for(let n=0;n<12;n++)walls.push([0,-4.95+n*.3,1.65,.31,(n+1)*.2]);","walls.push([0,0,3,3,2.4]);outdoorStairs.push({x:0,z:-5.10,dir:1,run:3.6,w:2.1,h:2.4});for(let n=0;n<12;n++)walls.push([0,-4.95+n*.3,2.1,.31,(n+1)*.2]);"],["function ground(x,z,y){let h=houseHeight(x,z,y);","function stairSupport(x,z,y){let h=0;for(let a of outdoorStairs){let d=(z-a.z)*a.dir;if(Math.abs(x-a.x)<a.w/2+.08&&d>=-.08&&d<=a.run+.25){let t=Math.max(0,Math.min(a.h,Math.ceil(Math.max(0,d)/.3)*.2));if(t<=y+.43)h=Math.max(h,t)}}return h}function ground(x,z,y){let h=Math.max(houseHeight(x,z,y),stairSupport(x,z,y));"],["t<=y+.26&&Math.abs(x-a)<w/2+.04&&Math.abs(z-b)<d/2+.04","t<=y+.34&&Math.abs(x-a)<w/2+.04&&Math.abs(z-b)<d/2+.04"],["t>Math.max(h,v.y)+.26&&Math.abs(x-a)<w/2+.20&&Math.abs(z-b)<d/2+.20","t>Math.max(h,v.y)+.34&&Math.abs(x-a)<w/2+.16&&Math.abs(z-b)<d/2+(stairSupport(x,z,v.y)>0?.025:.16)"],["o.hi>h+(o.stair?.30:.27)","o.hi>h+(o.stair?.43:.27)"],["floor>=base-.31&&floor<=base+.31","floor>=base-.43&&floor<=base+.43"],["if(moveClear(v.x+dx,v.z,v)&&(Math.abs(v.y-q.y)>1.6||Math.hypot(v.x+dx-q.x,v.z-q.z)>.55))v.x+=dx;if(moveClear(v.x,v.z+dz,v)&&(Math.abs(v.y-q.y)>1.6||Math.hypot(v.x-q.x,v.z+dz-q.z)>.55))v.z+=dz;","let steps=Math.max(1,Math.ceil(Math.hypot(dx,dz)/.075));for(let k=0;k<steps;k++){let sx=dx/steps,sz=dz/steps;if(moveClear(v.x+sx,v.z,v)&&(Math.abs(v.y-q.y)>1.6||Math.hypot(v.x+sx-q.x,v.z-q.z)>.55))v.x+=sx;if(moveClear(v.x,v.z+sz,v)&&(Math.abs(v.y-q.y)>1.6||Math.hypot(v.x-q.x,v.z+sz-q.z)>.55))v.z+=sz;if(v.grounded){let h=ground(v.x,v.z,v.y);if(Math.abs(h-v.y)<=.43)v.y=h;}}"]]
for _old, _new in _stair_replacements:
    if GAME_HTML.count(_old.encode()) != 1:
        print('Stair patch source mismatch: ' + _old[:80], flush=True)
        continue
    GAME_HTML = GAME_HTML.replace(_old.encode(), _new.encode(), 1)

GAME_HTML = GAME_HTML.replace(b'</head>', "<style>button,[role=\"button\"]{transition:transform 140ms cubic-bezier(.2,.8,.2,1),filter 140ms,box-shadow 180ms;-webkit-tap-highlight-color:transparent}button:active,[role=\"button\"]:active,.strike-pressed{transform:scale(.95);filter:brightness(1.25);box-shadow:0 0 18px #70caff66} @media(prefers-reduced-motion:reduce){button,[role=\"button\"]{transition:none}}</style><script>document.addEventListener('pointerdown',e=>{const b=e.target.closest('button,[role=\"button\"]');if(b){b.classList.add('strike-pressed');setTimeout(()=>b.classList.remove('strike-pressed'),180)}},true);</script>".encode() + b'</head>', 1)
# Three grenades per player at round reset.
GAME_HTML = GAME_HTML.replace(b'v.grenades=2;', b'v.grenades=3;')

# Keep canvas resizing in the render loop, before viewport and scissor are set.
for _old, _new in [["if(netRole)render(netRole==='host'?0:1);else{render(0);render(1);}","if(playing){if(netRole)render(netRole==='host'?0:1);else{render(0);render(1);}}"],["innerWidth*devicePixelRatio","innerWidth*Math.min(devicePixelRatio,1.5)"],["function render(i){let v=p[i],q=p[1-i],","let guestView=null;function render(i){let v=p[i],q=p[1-i];if(netRole==='guest'&&guestView){v={...v,x:guestView.x,z:guestView.z,visualY:guestView.y};}let "],["function guestTick(dt,t){if(!netReady)return;","function guestTick(dt,t){if(!netReady)return;const v=p[1];if(!guestView||Math.hypot(v.x-guestView.x,v.z-guestView.z)>1.4||delay||v.hp<=0)guestView={x:v.x,z:v.z,y:v.visualY};else{const k=1-Math.exp(-dt*28);guestView.x+=(v.x-guestView.x)*k;guestView.z+=(v.z-guestView.z)*k;guestView.y+=(v.visualY-guestView.y)*k;}"],["netRole=mode;netReady=false;","guestView=null;netRole=mode;netReady=false;"]]:
    if GAME_HTML.count(_old.encode()) != 1:
        raise RuntimeError('Online render patch source changed: ' + _old)
    GAME_HTML = GAME_HTML.replace(_old.encode(), _new.encode(), 1)

rooms = {}
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
                    if len(rooms) >= 100:
                        await send(ws, {'type': 'error', 'message': 'Server full.'})
                        continue
                    if not integer(m.get('map'), 0, 3):
                        continue
                    code = ''.join(secrets.choice('ABCDEFGHJKLMNPQRSTUVWXYZ23456789') for _ in range(6))
                    while code in rooms:
                        code = ''.join(secrets.choice('ABCDEFGHJKLMNPQRSTUVWXYZ23456789') for _ in range(6))
                    role = 'host'
                    rooms[code] = {'host': ws, 'guest': None, 'map': m['map'], 'hostGun': gun}
                    await send(ws, {'type': 'room', 'room': code})
                elif t == 'join':
                    candidate = m.get('room')
                    if not isinstance(candidate, str):
                        continue
                    r = rooms.get(candidate)
                    if not r or r['host'].closed or r['guest'] is not None:
                        await send(ws, {'type': 'error', 'message': 'Room not found or already full.'})
                        continue
                    code, role = candidate, 'guest'
                    r['guest'] = ws
                    start = {'type': 'start', 'room': code, 'map': r['map'], 'hostGun': r['hostGun'], 'guestGun': gun}
                    await asyncio.gather(send(r['host'], start), send(ws, start))
                continue
            r = rooms.get(code)
            if not r or r['guest'] is None:
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
        r = rooms.get(code)
        if r and r.get(role) is ws:
            rooms.pop(code, None)
            other = r['guest' if role == 'host' else 'host']
            await send(other, {'type': 'left'})
            if other is not None and not other.closed:
                await other.close()
    return ws

app = web.Application()
app.router.add_get('/', index)
app.router.add_get('/health', health)
app.router.add_get('/ws', websocket)

if __name__ == '__main__':
    web.run_app(app, host='0.0.0.0', port=int(os.environ.get('PORT', '8080')))
