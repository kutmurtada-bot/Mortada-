package com.tahen.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
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

import io.sentry.Sentry;
import io.sentry.SentryLevel;

public class MainActivity extends Activity {
    private static final String TAG = "FlourDistribution";
    private static final int CREATE_FILE = 7001;
    private static final int PICK_BACKUP_FILE = 7002;
    private ValueCallback<Uri[]> filePathCallback;
    private byte[] pendingBytes;
    private String pendingName;
    private String pendingMime;
    private boolean pendingOpen;

    private WebView webView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        initSentry();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                if (message == null) return true;
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    String text = message.message() + " @ " + message.sourceId() + ":" + message.lineNumber();
                    Sentry.captureMessage("WebView JavaScript error: " + text, SentryLevel.ERROR);
                    Log.e(TAG, text);
                }
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES,
                            new String[]{"application/json", "text/json", "application/octet-stream"});
                    startActivityForResult(Intent.createChooser(intent, "اختيار ملف النسخة الاحتياطية"),
                            PICK_BACKUP_FILE);
                    return true;
                } catch (Exception e) {
                    Sentry.captureException(e);
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "تعذر فتح مدير الملفات", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void initSentry() {
        final String dsn = BuildConfig.SENTRY_DSN == null ? "" : BuildConfig.SENTRY_DSN.trim();
        if (dsn.isEmpty()) {
            Log.w(TAG, "Sentry is not configured. Add the SENTRY_DSN environment variable for production builds.");
            return;
        }
        try {
            Sentry.init(options -> {
                options.setDsn(dsn);
                options.setEnvironment("prod");
                options.setRelease(getPackageName() + "@" + BuildConfig.VERSION_NAME + "+" + BuildConfig.VERSION_CODE);
                options.setSendDefaultPii(false);
                options.setEnableAutoSessionTracking(true);
            });
        } catch (Exception e) {
            Log.e(TAG, "Sentry initialization failed", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_BACKUP_FILE) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }

    private byte[] decode(String b64) {
        if (Build.VERSION.SDK_INT >= 26) return Base64.getDecoder().decode(b64);
        return android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void saveFile(String name, String mime, String base64, boolean openAfter) {
            try {
                pendingBytes = decode(base64);
                pendingName = name;
                pendingMime = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
                pendingOpen = openAfter;
            } catch (Exception e) {
                Sentry.captureException(e);
                Toast.makeText(MainActivity.this, "تعذر تجهيز الملف للحفظ", Toast.LENGTH_LONG).show();
                return;
            }
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
                            if (out == null) throw new Exception("output stream unavailable");
                            out.write(pendingBytes);
                        }
                        v.clear();
                        v.put(MediaStore.Downloads.IS_PENDING, 0);
                        getContentResolver().update(uri, v, null, null);
                        if (pendingOpen) openUri(uri, pendingMime);
                        else Toast.makeText(MainActivity.this, "تم حفظ الملف في التنزيلات", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Sentry.captureException(e);
                        Toast.makeText(MainActivity.this, "تعذر حفظ الملف: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    try {
                        File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                        if (dir == null) throw new Exception("downloads directory unavailable");
                        File f = new File(dir, pendingName);
                        try (FileOutputStream out = new FileOutputStream(f)) { out.write(pendingBytes); }
                        if (pendingOpen) openUri(Uri.fromFile(f), pendingMime);
                        else Toast.makeText(MainActivity.this, "تم حفظ الملف", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Sentry.captureException(e);
                        Toast.makeText(MainActivity.this, "تعذر حفظ الملف", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void shareFile(String name, String mime, String base64) {
            try {
                byte[] data = decode(base64);
                runOnUiThread(() -> {
                    try {
                        File dir = new File(getCacheDir(), "shared");
                        if (!dir.exists() && !dir.mkdirs()) throw new Exception("cache directory unavailable");
                        File f = new File(dir, name);
                        try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
                        Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", f);
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType(mime == null ? "application/octet-stream" : mime);
                        i.putExtra(Intent.EXTRA_STREAM, uri);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, "مشاركة الملف"));
                    } catch (Exception e) {
                        Sentry.captureException(e);
                        Toast.makeText(MainActivity.this, "تعذرت مشاركة الملف", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Sentry.captureException(e);
                Toast.makeText(MainActivity.this, "تعذر تجهيز الملف للمشاركة", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openUri(Uri uri, String mime) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Sentry.captureException(e);
            Toast.makeText(this, "تم حفظ الملف. لا يوجد تطبيق مناسب لفتحه مباشرة.", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
