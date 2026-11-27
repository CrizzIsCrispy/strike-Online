# STRIKE ELITE V12 — Online Multiplayer 1v1

A browser-based tactical first-person shooter for two phones. Run the web service, open its HTTPS URL on both phones, then create or join the same six-character room.

## Deployment

Render Web Service:
- Python 3
- Build: `pip install -r requirements.txt`
- Start: `python server.py`
- Region: Singapore
- Health check: `/health`

The relay handles WebSockets at `/ws`. The host browser simulates both players and the guest receives snapshots. Keep both browsers open during the match.

The original game HTML is losslessly archived in `public/game_01.txt` … `public/game_07.txt` to fit the publishing workflow. The Python server decompresses it at startup and serves it on `/`. To extract an editable copy:

```python
import lzma, base64
from pathlib import Path
Path("index.html").write_bytes(lzma.decompress(base64.b64decode("".join(Path(f"public/game_{i:02}.txt").read_text() for i in range(1,8)))))
```

## Current limitations
No matchmaking, authentication, voice chat, reconnection, anti-cheat or server-authoritative combat. The relay stores rooms in process memory, so use a single instance. The host must stay connected. Free service may spin down when idle and sleeping can interrupt matches. Target 60 FPS is not a guarantee.
