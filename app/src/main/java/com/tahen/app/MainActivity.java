package com.tahen.app;

import android.app.Activity;
import android.app.PrintManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import io.sentry.Sentry;
import io.sentry.SentryLevel;

public class MainActivity extends Activity {
    private static final String TAG = "Sanabil";
    private static final int PICK_BACKUP_FILE = 7002;
    private static final int PICK_DRIVE_TREE = 7003;
    private static final String PREFS = "sanabil_native";
    private static final String DRIVE_TREE = "drive_tree_uri";

    private ValueCallback<Uri[]> filePathCallback;
    private WebView webView;
    private WebView printWebView;
    private FrameLayout root;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        configureWebView(webView);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void configureWebView(WebView view) {
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setWebViewClient(new WebViewClient());
        view.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                if (message != null && message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    String text = message.message() + " @ " + message.sourceId() + ":" + message.lineNumber();
                    Sentry.captureMessage("WebView JavaScript error: " + text, SentryLevel.ERROR);
                    Log.e(TAG, text);
                }
                return true;
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    startActivityForResult(Intent.createChooser(intent, "اختيار ملف النسخة الاحتياطية"), PICK_BACKUP_FILE);
                    return true;
                } catch (Throwable e) {
                    Sentry.captureException(e);
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "تعذر فتح مدير الملفات", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_BACKUP_FILE) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null && data.getData() != null) results = new Uri[]{data.getData()};
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        } else if (requestCode == PICK_DRIVE_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Throwable e) { Log.w(TAG, "Could not persist Drive folder permission", e); }
            prefs.edit().putString(DRIVE_TREE, uri.toString()).apply();
            runOnUiThread(() -> webView.evaluateJavascript("window.dispatchEvent(new Event('drive-connected'))", null));
            Toast.makeText(this, "تم ربط مجلد التخزين ✓", Toast.LENGTH_SHORT).show();
        }
    }

    private Uri driveUri() {
        String value = prefs.getString(DRIVE_TREE, "");
        if (value == null || value.isEmpty()) return null;
        try { return Uri.parse(value); } catch (Throwable e) { return null; }
    }

    private byte[] decode(String b64) { return Base64.decode(b64, Base64.DEFAULT); }

    private void saveToDownloads(String name, String mime, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(android.provider.MediaStore.Downloads.MIME_TYPE, mime);
            v.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("download insert failed");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("download output unavailable");
                out.write(data);
            }
            v.clear(); v.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, v, null, null);
        } else {
            File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) throw new Exception("downloads directory unavailable");
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
        }
    }

    private boolean saveToDrive(String name, String mime, byte[] data) {
        Uri tree = driveUri();
        if (tree == null) return false;
        try {
            DocumentFile dir = DocumentFile.fromTreeUri(this, tree);
            if (dir == null || !dir.canWrite()) return false;
            DocumentFile old = dir.findFile(name);
            if (old != null) old.delete();
            DocumentFile file = dir.createFile(mime, name);
            if (file == null) return false;
            try (OutputStream out = getContentResolver().openOutputStream(file.getUri())) {
                if (out == null) return false;
                out.write(data);
            }
            return true;
        } catch (Throwable e) {
            Sentry.captureException(e);
            return false;
        }
    }

    private void printHtml(String title, String html) {
        try {
            if (printWebView != null) { root.removeView(printWebView); printWebView.destroy(); }
            printWebView = new WebView(this);
            configureWebView(printWebView);
            printWebView.setVisibility(WebView.INVISIBLE);
            root.addView(printWebView, new FrameLayout.LayoutParams(1, 1));
            printWebView.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    try {
                        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                        pm.print(title == null || title.isEmpty() ? "Sanabil" : title, view.createPrintDocumentAdapter(title), null);
                    } catch (Throwable e) {
                        Sentry.captureException(e);
                        Toast.makeText(MainActivity.this, "تعذرت تهيئة الطباعة", Toast.LENGTH_LONG).show();
                    }
                }
            });
            printWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        } catch (Throwable e) {
            Sentry.captureException(e);
            Toast.makeText(this, "تعذرت الطباعة", Toast.LENGTH_LONG).show();
        }
    }

    public class AndroidBridge {
        @JavascriptInterface public void saveFile(String name, String mime, String base64, boolean openAfter) {
            try {
                saveToDownloads(name, mime == null || mime.isEmpty() ? "application/octet-stream" : mime, decode(base64));
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تم حفظ الملف في التنزيلات ✓", Toast.LENGTH_SHORT).show());
            } catch (Throwable e) {
                Sentry.captureException(e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تعذر حفظ الملف", Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface public void shareFile(String name, String mime, String base64) {
            try {
                File dir = new File(getCacheDir(), "shared");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("cache directory unavailable");
                File f = new File(dir, name);
                try (FileOutputStream out = new FileOutputStream(f)) { out.write(decode(base64)); }
                Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", f);
                Intent i = new Intent(Intent.ACTION_SEND).setType(mime == null ? "application/octet-stream" : mime);
                i.putExtra(Intent.EXTRA_STREAM, uri); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "مشاركة الملف"));
            } catch (Throwable e) {
                Sentry.captureException(e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تعذرت مشاركة الملف", Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface public void printHtml(String title, String html) { runOnUiThread(() -> printHtml(title, html)); }

        @JavascriptInterface public void pickDriveFolder() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(i, PICK_DRIVE_TREE);
                } catch (Throwable e) {
                    Sentry.captureException(e);
                    Toast.makeText(MainActivity.this, "تعذر فتح اختيار التخزين", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public boolean hasDriveFolder() { return driveUri() != null; }

        @JavascriptInterface public boolean saveToDrive(String name, String mime, String base64) {
            boolean ok = MainActivity.this.saveToDrive(name, mime == null ? "application/octet-stream" : mime, decode(base64));
            runOnUiThread(() -> Toast.makeText(MainActivity.this, ok ? "تم حفظ النسخة في Google Drive ✓" : "اربط مجلد Google Drive أولاً", Toast.LENGTH_SHORT).show());
            return ok;
        }
    }

    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }

    @Override protected void onDestroy() {
        if (printWebView != null) { root.removeView(printWebView); printWebView.destroy(); printWebView = null; }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
