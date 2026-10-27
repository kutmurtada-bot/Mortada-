package com.tahen.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Base64;

public class MainActivity extends Activity {
    private static final int CREATE_FILE = 7001;
    private byte[] pendingBytes;
    private String pendingName;
    private String pendingMime;
    private boolean pendingOpen;

    private WebView webView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private byte[] decode(String b64) {
        if (Build.VERSION.SDK_INT >= 26) return Base64.getDecoder().decode(b64);
        return android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void saveFile(String name, String mime, String base64, boolean openAfter) {
            pendingBytes = decode(base64);
            pendingName = name;
            pendingMime = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
            pendingOpen = openAfter;
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 29) {
                    try {
                        ContentValues v = new ContentValues();
                        v.put(MediaStore.Downloads.DISPLAY_NAME, pendingName);
                        v.put(MediaStore.Downloads.MIME_TYPE, pendingMime);
                        v.put(MediaStore.Downloads.IS_PENDING, 1);
                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                        if (uri == null) throw new Exception("insert failed");
                        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                            out.write(pendingBytes);
                        }
                        v.clear();
                        v.put(MediaStore.Downloads.IS_PENDING, 0);
                        getContentResolver().update(uri, v, null, null);
                        if (pendingOpen) openUri(uri, pendingMime);
                        else Toast.makeText(MainActivity.this, "تم حفظ الملف في التنزيلات", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "تعذر حفظ الملف: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    try {
                        File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                        File f = new File(dir, pendingName);
                        try (FileOutputStream out = new FileOutputStream(f)) { out.write(pendingBytes); }
                        if (pendingOpen) openUri(Uri.fromFile(f), pendingMime);
                        else Toast.makeText(MainActivity.this, "تم حفظ الملف", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "تعذر حفظ الملف", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void shareFile(String name, String mime, String base64) {
            byte[] data = decode(base64);
            runOnUiThread(() -> {
                try {
                    File dir = new File(getCacheDir(), "shared");
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, name);
                    try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", f);
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType(mime == null ? "application/octet-stream" : mime);
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "مشاركة الملف"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذرت مشاركة الملف", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void openUri(Uri uri, String mime) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "تم حفظ الملف. لا يوجد تطبيق مناسب لفتحه مباشرة.", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
