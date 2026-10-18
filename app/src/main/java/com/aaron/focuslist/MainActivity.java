package com.aaron.focuslist;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Alarms.ensureChannels(this);

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "FocusNative");
        setContentView(web);

        if (saved != null) web.restoreState(saved);
        else web.loadUrl("file:///android_asset/index.html");

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Let the page catch up on a period that ended while the app was in the background.
        if (web != null) web.evaluateJavascript("window.dispatchEvent(new Event('focus'))", null);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true); // keep the timer state instead of closing the page
    }

    private MediaPlayer player;

    private void playSound(String kind) {
        try {
            if (player != null) { player.release(); player = null; }
            int raw = "rest".equals(kind) ? R.raw.rest : R.raw.rooster;
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(this, Uri.parse("android.resource://" + getPackageName() + "/" + raw));
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.prepare();
            mp.start();
            player = mp;
        } catch (Exception ignored) {
        }
    }

    /** Called from the page's JavaScript as window.FocusNative. */
    class Bridge {
        @JavascriptInterface
        public void schedule(double atMillis, String title, String body, String kind) {
            Alarms.schedule(getApplicationContext(), (long) atMillis, title, body, kind);
        }

        /** Plays the rooster or the ding-and-clucks right away, on the alarm stream like the alarms themselves. */
        @JavascriptInterface
        public void play(String kind) {
            runOnUiThread(() -> playSound(kind));
        }

        @JavascriptInterface
        public void cancel() {
            Alarms.cancel(getApplicationContext());
        }
    }
}
