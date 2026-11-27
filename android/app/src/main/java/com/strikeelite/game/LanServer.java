package com.strikeelite.game;
import fi.iki.elonen.NanoWSD;
import java.io.IOException;
import java.security.SecureRandom;
import org.json.JSONObject;

final class LanServer extends NanoWSD {
    static final int PORT = 8765;
    private final String page;
    private final java.util.function.Consumer<String> roomChanged;
    private Peer host, guest;
    private JSONObject start;
    private String code;
    LanServer(String page, java.util.function.Consumer<String> roomChanged) {
        super(PORT);
        this.page=page;
        this.roomChanged=roomChanged;
    }

    @Override public Response serveHttp(IHTTPSession request) {
        if (request.getMethod()!=Method.GET || !"/".equals(request.getUri()))
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found");
        Response response=newFixedLengthResponse(Response.Status.OK,"text/html; charset=utf-8",page);
        response.addHeader("Cache-Control","no-store");
        return response;
    }
    @Override protected WebSocket openWebSocket(IHTTPSession request) {
        String origin=request.getHeaders().get("origin");
        String authority=request.getHeaders().get("host");
        boolean sameOrigin=origin!=null && origin.equals("http://"+authority);
        return new Peer(request, sameOrigin, "127.0.0.1".equals(request.getRemoteIpAddress()));
    }
    private void deliver(Peer peer, JSONObject data) throws IOException {
        if (peer!=null && peer.isOpen()) peer.send(data.toString());
    }
    private synchronized void receive(Peer peer,String payload) throws Exception {
        if (!peer.accepted || payload.length()>262144) return;
        JSONObject message=new JSONObject(payload);
        String type=message.optString("type");
        switch(type) {
            case "ping":
                deliver(peer,new JSONObject().put("type","pong").put("t",message.getDouble("t")));
                break;
            case "create":
                if (!peer.local || host!=null) {
                    deliver(peer,new JSONObject().put("type","error").put("message","Guna telefon hos untuk buat bilik."));
                    return;
                }
                int map=message.optInt("map",-1), gun=message.optInt("gun",-1);
                if(map<0 || map>3 || gun<0 || gun>5) return;
                code=String.format(java.util.Locale.ROOT,"%06d",new SecureRandom().nextInt(1000000));
                start=new JSONObject().put("type","start").put("room",code).put("map",map).put("hostGun",gun);
                host=peer;
                roomChanged.accept(code);
                deliver(host,new JSONObject().put("type","room").put("room",code));
                break;
            case "join":
                if(host==null || peer==host || guest!=null || !code.equals(message.optString("room"))) {
                    deliver(peer,new JSONObject().put("type","error").put("message","Kod salah atau bilik penuh."));
                    return;
                }
                int guestGun=message.optInt("gun",-1);
                if(guestGun<0 || guestGun>5) return;
                guest=peer;
                roomChanged.accept(null);
                start.put("guestGun",guestGun);
                deliver(host,start);
                deliver(guest,start);
                break;
            case "input":
                if(peer==guest) deliver(host,message);
                break;
            case "state":
                if(peer==host) deliver(guest,message);
                break;
        }
    }
    private synchronized void disconnected(Peer peer) {
        if(peer!=host && peer!=guest) return;
        Peer other=peer==host?guest:host;
        host=guest=null; start=null; code=null;
        roomChanged.accept(null);
        try { deliver(other,new JSONObject().put("type","left")); } catch(Exception ignored) {}
    }
    private final class Peer extends WebSocket {
        final boolean accepted,local;
        Peer(IHTTPSession request,boolean accepted,boolean local) {
            super(request); this.accepted=accepted; this.local=local;
        }
        @Override protected void onOpen() {
            if(!accepted) try { close(WebSocketFrame.CloseCode.PolicyViolation,"Origin mismatch",false); } catch(IOException ignored) {}
        }
        @Override protected void onClose(WebSocketFrame.CloseCode code,String reason,boolean remote) { disconnected(this); }
        @Override protected void onMessage(WebSocketFrame frame) {
            try { receive(this,frame.getTextPayload()); } catch(Exception ignored) {}
        }
        @Override protected void onPong(WebSocketFrame frame) {}
        @Override protected void onException(IOException error) { disconnected(this); }
    }
}
