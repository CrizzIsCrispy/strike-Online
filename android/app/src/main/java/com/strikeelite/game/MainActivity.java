package com.strikeelite.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;
import android.widget.EditText;
import android.text.InputType;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.TreeSet;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebViewClient;

public final class MainActivity extends Activity {
    private WebView game;
    private LanServer lan;
    private String lanAddress;
    private static final String LAN_TYPE="_strikeelite._tcp.";
    private NsdManager.RegistrationListener announcement;
    private NsdManager.DiscoveryListener scan;

    private NsdManager nsd() { return getSystemService(NsdManager.class); }

    private void hideRoom() {
        if(announcement==null)return;
        NsdManager.RegistrationListener previous=announcement;
        announcement=null;
        try { nsd().unregisterService(previous); } catch(IllegalArgumentException ignored) {}
    }

    private void advertiseRoom(String code) {
        hideRoom();
        if(code==null || lan==null || isFinishing())return;
        NsdServiceInfo service=new NsdServiceInfo();
        service.setServiceName("Strike Elite "+code);
        service.setServiceType(LAN_TYPE);
        service.setPort(LanServer.PORT);
        service.setAttribute("room",code);
        announcement=new NsdManager.RegistrationListener(){
            @Override public void onServiceRegistered(NsdServiceInfo info){}
            @Override public void onRegistrationFailed(NsdServiceInfo info,int error){
                runOnUiThread(()->{
                    if(announcement!=this)return;
                    announcement=null;
                    Toast.makeText(MainActivity.this,"Senarai LAN tak tersedia. Guna IP dan kod bilik.",Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onServiceUnregistered(NsdServiceInfo info){}
            @Override public void onUnregistrationFailed(NsdServiceInfo info,int error){}
        };
        try { nsd().registerService(service,NsdManager.PROTOCOL_DNS_SD,announcement); }
        catch(IllegalArgumentException error){ announcement=null; }
    }

    private void stopScan() {
        if(scan==null)return;
        NsdManager.DiscoveryListener previous=scan;
        scan=null;
        try { nsd().stopServiceDiscovery(previous); } catch(IllegalArgumentException ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void joinLan() {
        LinkedHashMap<String,NsdServiceInfo> rooms=new LinkedHashMap<>();
        ArrayList<String> names=new ArrayList<>();
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,names);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Cari bilik LAN")
            .setAdapter(adapter,null)
            .setNegativeButton("Batal",null).setNeutralButton("Masukkan IP",(d,w)->joinLanManual())
            .create();
        android.widget.TextView empty=new android.widget.TextView(this);
        empty.setText("Mencari bilik pada Wi-Fi atau hotspot yang sama…");
        empty.setPadding(24,20,24,20);
        dialog.setOnDismissListener(d->stopScan());
        dialog.show();
        android.widget.ListView list=dialog.getListView();
        list.addHeaderView(empty,null,false);
        // The header changes list positions, so handle entries by item rather than index.
        list.setOnItemClickListener((parent,view,position,id)->{
            Object item=parent.getItemAtPosition(position);
            if(!(item instanceof String))return;
            dialog.getListView().setItemChecked(position,true);
            NsdServiceInfo selected=rooms.get((String)item);
            if(selected!=null){dialog.dismiss();connectRoom(selected);}
        });
        scan=new NsdManager.DiscoveryListener(){
            private void change(NsdServiceInfo info,boolean add){
                runOnUiThread(()->{
                    if(scan!=this || !dialog.isShowing())return;
                    String name=info.getServiceName();
                    if(add)rooms.put(name,info);else rooms.remove(name);
                    names.clear();names.addAll(rooms.keySet());adapter.notifyDataSetChanged();
                    empty.setText(names.isEmpty()?"Mencari bilik LAN…":"Pilih bilik untuk masuk");
                });
            }
            @Override public void onDiscoveryStarted(String type){}
            @Override public void onDiscoveryStopped(String type){}
            @Override public void onServiceFound(NsdServiceInfo info){change(info,true);}
            @Override public void onServiceLost(NsdServiceInfo info){change(info,false);}
            @Override public void onStartDiscoveryFailed(String type,int error){
                runOnUiThread(()->{if(scan==this){scan=null;empty.setText("Carian gagal. Cuba guna alamat IP.");}});
            }
            @Override public void onStopDiscoveryFailed(String type,int error){}
        };
        try { nsd().discoverServices(LAN_TYPE,NsdManager.PROTOCOL_DNS_SD,scan); }
        catch(IllegalArgumentException error){scan=null;empty.setText("Carian gagal. Cuba guna alamat IP.");}
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        game = new WebView(this);
        game.setBackgroundColor(0xff101820);
        game.getSettings().setJavaScriptEnabled(true);
        game.getSettings().setAllowFileAccess(false);
        game.getSettings().setAllowContentAccess(false);
        
        game.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if ("strike".equals(r.getUrl().getScheme()) && r.isForMainFrame()
                        && "file:///android_asset/index.html".equals(v.getUrl())) {
                    if ("host".equals(r.getUrl().getHost())) hostLan();
                    if ("join".equals(r.getUrl().getHost())) joinLan();
                    return true;
                }
                boolean local = "http".equals(r.getUrl().getScheme())
                        && r.getUrl().getPort() == LanServer.PORT
                        && lanAddress != null && lanAddress.equals(r.getUrl().getHost());
                return !(local || "file:///android_asset/index.html".equals(r.getUrl().toString()) || ("https".equals(r.getUrl().getScheme()) && "strike-elite-v12-online.onrender.com".equals(r.getUrl().getHost()) && r.getUrl().getPort() == -1));
            }
        });
        game.setWebChromeClient(new WebChromeClient());
        setContentView(game);
        game.loadUrl("file:///android_asset/index.html");
        immersive();
    }
    @SuppressWarnings("deprecation")
    private void connectRoom(NsdServiceInfo selected) {
                nsd().resolveService(selected,new NsdManager.ResolveListener(){
                    @Override public void onResolveFailed(NsdServiceInfo info,int error){
                        runOnUiThread(()->Toast.makeText(MainActivity.this,"Bilik tak dapat dicapai. Cuba Join LAN semula.",Toast.LENGTH_LONG).show());
                    }
                    @Override public void onServiceResolved(NsdServiceInfo info){
                        runOnUiThread(()->{
                            if(isFinishing() || isDestroyed())return;
                            String address=info.getHost()==null?null:info.getHost().getHostAddress();
                            byte[] bytes=info.getAttributes().get("room");
                            String code=bytes==null?"":new String(bytes,StandardCharsets.US_ASCII);
                            if(!privateAddress(address)||info.getPort()!=LanServer.PORT||!code.matches("[0-9]{6}")){
                                Toast.makeText(MainActivity.this,"Bilik ini tak tersedia. Cuba Join LAN semula.",Toast.LENGTH_LONG).show();
                                return;
                            }
                            lanAddress=address;
                            game.loadUrl("http://"+address+":"+LanServer.PORT+"/#join="+code);
                        });
                    }
                });
    }

    private static boolean privateAddress(String address) {
        if (address == null || !address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        String[] parts=address.split("\\.");
        for (String part:parts) if (Integer.parseInt(part)>255 || (part.length()>1 && part.startsWith("0"))) return false;
        int a=Integer.parseInt(parts[0]), b=Integer.parseInt(parts[1]);
        return a==10 || (a==192 && b==168) || (a==172 && b>=16 && b<=31);
    }

    private void hostLan() {
        TreeSet<String> addresses=new TreeSet<>();
        try {
            for (NetworkInterface link:Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!link.isUp() || link.isLoopback()) continue;
                for (java.net.InetAddress address:Collections.list(link.getInetAddresses())) {
                    if (address instanceof Inet4Address && privateAddress(address.getHostAddress())) addresses.add(address.getHostAddress());
                }
            }
        } catch (IOException ignored) {}
        if (addresses.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("Sambung Wi-Fi atau hidupkan hotspot dahulu.").setPositiveButton("OK",null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Host LAN")
            .setMessage("Alamat hos: "+String.join(" / ",addresses)+"\nTekan Teruskan, cipta bilik dan kongsi alamat serta kod bilik.")
            .setNegativeButton("Batal",null).setPositiveButton("Teruskan",(dialog,which)->{
                try (InputStream asset=getAssets().open("lan.html"); ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[8192];
                    for(int len;(len=asset.read(buffer))!=-1;) bytes.write(buffer,0,len);
                    stopLan();
                    lan=new LanServer(bytes.toString("UTF-8"), code->runOnUiThread(()->advertiseRoom(code)));
                    lan.start(5000,false);
                    openLan("127.0.0.1");
                } catch(IOException e) {
                    stopLan();
                    new AlertDialog.Builder(this).setMessage("Server LAN tak dapat dimulakan.").setPositiveButton("OK",null).show();
                }
            }).show();
    }
    private void joinLanManual() {
        EditText address=new EditText(this);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_PHONE);
        address.setHint("192.168.1.10");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Join LAN")
            .setMessage("Masukkan alamat telefon hos.").setView(address)
            .setNegativeButton("Batal",null).setPositiveButton("Sambung",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String value=address.getText().toString().trim();
            if(!privateAddress(value)){address.setError("Masukkan alamat Wi-Fi tempatan.");return;}
            dialog.dismiss();
            openLan(value);
        }));
        dialog.show();
    }
    private void openLan(String address) {
        lanAddress=address;
        game.loadUrl("http://"+address+":"+LanServer.PORT+"/");
    }
    private void stopLan() {
        hideRoom();
        stopScan();
        if(lan!=null){lan.stop();lan=null;}
        lanAddress=null;
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) immersive();
    }
    @SuppressWarnings("deprecation")
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (game.getUrl() != null && (game.getUrl().startsWith("https://") || lanAddress != null)) {
            stopLan();
            game.loadUrl("file:///android_asset/index.html");
        } else super.onBackPressed();
    }
    @Override protected void onPause() { game.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); game.onResume(); }
    @Override protected void onDestroy() { stopLan(); game.destroy(); super.onDestroy(); }
}
