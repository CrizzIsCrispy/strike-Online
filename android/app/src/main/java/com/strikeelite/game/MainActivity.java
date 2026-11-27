package com.strikeelite.game;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebViewClient;

public final class MainActivity extends Activity {
    private WebView game;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        game = new WebView(this);
        game.setBackgroundColor(0xff101820);
        game.getSettings().setJavaScriptEnabled(true);
        game.getSettings().setAllowFileAccess(false);
        game.getSettings().setAllowContentAccess(false);
        
        game.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return !("file:///android_asset/index.html".equals(r.getUrl().toString()) || ("https".equals(r.getUrl().getScheme()) && "strike-elite-v12-online.onrender.com".equals(r.getUrl().getHost()) && r.getUrl().getPort() == -1));
            }
        });
        game.setWebChromeClient(new WebChromeClient());
        setContentView(game);
        game.loadUrl("file:///android_asset/index.html");
        immersive();
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
        if (game.getUrl() != null && game.getUrl().startsWith("https://")) {
            game.loadUrl("file:///android_asset/index.html");
        } else super.onBackPressed();
    }
    @Override protected void onPause() { game.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); game.onResume(); }
    @Override protected void onDestroy() { game.destroy(); super.onDestroy(); }
}
