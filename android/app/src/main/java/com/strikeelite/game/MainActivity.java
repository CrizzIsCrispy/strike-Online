package com.strikeelite.game;

import android.app.Activity;
import android.app.AlertDialog;
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
                    lan=new LanServer(bytes.toString("UTF-8"));
                    lan.start(5000,false);
                    openLan("127.0.0.1");
                } catch(IOException e) {
                    stopLan();
                    new AlertDialog.Builder(this).setMessage("Server LAN tak dapat dimulakan.").setPositiveButton("OK",null).show();
                }
            }).show();
    }
    private void joinLan() {
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
