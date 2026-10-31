package com.tahen.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

public class SafeLaunchActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        try {
            WebView webView = new WebView(this);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            webView.setWebViewClient(new WebViewClient());
            setContentView(webView);
            webView.loadUrl("file:///android_asset/index.html");
        } catch (Throwable error) {
            TextView fallback = new TextView(this);
            fallback.setText("تعذر تشغيل واجهة التطبيق.\n\n" + error.getClass().getSimpleName() + "\n" + String.valueOf(error.getMessage()));
            fallback.setTextSize(16);
            fallback.setTextColor(Color.DKGRAY);
            fallback.setGravity(Gravity.CENTER);
            fallback.setPadding(32, 32, 32, 32);
            setContentView(fallback);
        }
    }
}
